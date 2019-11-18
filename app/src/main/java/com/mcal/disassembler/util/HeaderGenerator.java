package com.mcal.disassembler.util;

import com.mcal.disassembler.nativeapi.DisassemblerClass;
import com.mcal.disassembler.nativeapi.DisassemblerSymbol;
import com.mcal.disassembler.nativeapi.DisassemblerVtable;

import java.util.Vector;

public class HeaderGenerator {
    private DisassemblerClass disassemblerClass;
    private DisassemblerVtable vtable;
    private String[] namespace;
    private String className;

    public HeaderGenerator(DisassemblerClass disassemblerClass, DisassemblerVtable vtable, String path) {
        this.disassemblerClass = disassemblerClass;
        this.vtable = vtable;

        this.className = disassemblerClass.getName();
        if (this.className.lastIndexOf("::") != -1) {
            this.className = this.className.substring(this.className.lastIndexOf("::") + 2);
        }
        try {
            String namespaces = disassemblerClass.getName().substring(0, disassemblerClass.getName().length() - className.length() - 2);
            this.namespace = namespaces.split("::");
        } catch (Exception e) {
            this.namespace = new String[0];
            e.printStackTrace();
        }
    }

    private static boolean hasItemInList(Vector<DisassemblerSymbol> syms, DisassemblerSymbol sym) {
        for (DisassemblerSymbol iSym : syms)
            if (sym.getDemangledName().equals(iSym.getDemangledName()))
                return false;
        return true;
    }

    private static boolean isObjectItem(DisassemblerSymbol sym) {
        String dname = sym.getDemangledName();
        String name = sym.getName();
        return !dname.contains("(") && !dname.contains(")") && name.startsWith("_ZN");
    }

    private static boolean isMethodItem(DisassemblerSymbol sym) {
        String dname = sym.getDemangledName();
        String name = sym.getName();
        return dname.contains("(") && dname.contains(")") && name.startsWith("_ZN");
    }

    private String[] getExtendedClasses() {
        try {
            Vector<String> otherZTVs = new Vector<>();
            try {
                for (DisassemblerSymbol sym : vtable.getVtables())
                    if (isMethodBelongToOtherClass(sym)) {
                        boolean hasItem = false;
                        for (String str : otherZTVs)
                            if (str.equals(getOwnerClass(sym)))
                                hasItem = true;
                        if (!hasItem)
                            otherZTVs.addElement(getOwnerClass(sym));
                    }
            } catch (Exception e) {
                return null;
            }
            if (otherZTVs.isEmpty())
                return null;
            String[] ret = new String[otherZTVs.size()];
            for (int i = 0; i < otherZTVs.size(); ++i)
                ret[i] = otherZTVs.get(i);
            return ret;
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isMethodBelongToOtherClass(DisassemblerSymbol symbol) {
        return !symbol.getDemangledName().startsWith(disassemblerClass.getName());
    }

    private String getOwnerClass(DisassemblerSymbol sym) throws Exception {
        try {
            String dname = sym.getDemangledName().substring(0, sym.getDemangledName().indexOf("("));
            return dname.substring(0, dname.lastIndexOf("::"));
        } catch (Exception e) {
            throw new Exception("No owner class found.");
        }
    }

    public String[] generate() {
        Vector<String> lines = new Vector<>();
        try {
            lines.addElement("#pragma once");
            lines.addElement("");
            lines.addElement("//This header template file is generated by Disassembler.");
            lines.addElement("");

            for (String space : namespace) {
                lines.addElement("namespace " + space);
                lines.addElement("{");
            }

            lines.addElement("class " + className);
            String[] extendedClasses = getExtendedClasses();
            if (extendedClasses != null) {
                for (String str : extendedClasses) {
                    lines.addElement(" : public " + str);
                }
            }

            lines.addElement("{");
            lines.addElement("public:");
            lines.addElement("	//Fields");
            lines.addElement("	char filler_" + className + "[UNKNOW_SIZE];");

            if (getVtables() != null) {
                lines.addElement("public:");
                lines.addElement("	//Virtual Tables");

                for (DisassemblerSymbol symbol : getVtables()) {
                    try {
                        String mname = getVirtualMethodDefinition(symbol);
                        lines.addElement("	" + mname);
                    } catch (Exception ignored) {
                    }
                }
            }

            if (getMethods() != null) {
                lines.addElement("public:");
                lines.addElement("	//Methods");

                for (DisassemblerSymbol symbol : getMethods()) {
                    String mname = getMethodDefinition(symbol);
                    lines.addElement("	" + mname);
                }
            }

            if (getObjects() != null) {
                lines.addElement("public:");
                lines.addElement("	//Objects");
                for (DisassemblerSymbol symbol : getObjects()) {
                    String mname = getObjectDefinition(symbol);
                    lines.addElement("	" + mname);
                }
            }
            lines.addElement("};//" + className);
            for (String space : namespace) {
                lines.addElement("}//" + space);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        String[] ret = new String[lines.size()];
        for (int i = 0; i < lines.size(); ++i)
            ret[i] = lines.get(i);
        return ret;
    }

    private String getObjectDefinition(DisassemblerSymbol symbol) {
        String name = symbol.getDemangledName().substring(disassemblerClass.getName().length() + 2);
        return "static " + disassemblerClass.getName() + " * " + name + ";";
    }

    private String getMethodDefinition(DisassemblerSymbol symbol) {
        String name = symbol.getDemangledName().substring(disassemblerClass.getName().length() + 2);
        if (name.startsWith("~" + className))
            return name + ";";
        if (name.startsWith(className))
            return name + ";";
        return "void " + name + ";";
    }

    private String getVirtualMethodDefinition(DisassemblerSymbol symbol) throws Exception {
        String name_ = symbol.getDemangledName();
        if (name_.equals("__cxa_pure_virtual"))
            return "//pure virtual method";
        if (!name_.startsWith(disassemblerClass.getName()))
            throw new Exception("No owned vtable");
        String name = symbol.getDemangledName().substring(disassemblerClass.getName().length() + 2);
        if (name.startsWith("~" + className))
            return "virtual " + name + ";";
        if (name.startsWith(className))
            return "virtual " + name + ";";
        return "virtual void " + name + ";";
    }

    private DisassemblerSymbol[] getObjects() {
        Vector<DisassemblerSymbol> symbols = new Vector<DisassemblerSymbol>();
        for (DisassemblerSymbol symbol : disassemblerClass.getSymbols())
            if (isObjectItem(symbol))
                symbols.addElement(symbol);
        DisassemblerSymbol[] ret = new DisassemblerSymbol[symbols.size()];
        for (int i = 0; i < symbols.size(); ++i)
            ret[i] = symbols.get(i);
        if (symbols.isEmpty())
            return null;
        return ret;
    }

    private DisassemblerSymbol[] getVtables() {
        if (vtable == null)
            return null;
        if (vtable.getVtables().isEmpty())
            return null;
        Vector<DisassemblerSymbol> symbols = vtable.getVtables();
        for (DisassemblerSymbol symbol : symbols)
            if (hasItemInList(symbols, symbol))
                symbols.addElement(symbol);
        symbols = moveConOrDesToStart(symbols);
        DisassemblerSymbol[] ret = new DisassemblerSymbol[symbols.size()];
        for (int i = 0; i < symbols.size(); ++i)
            ret[i] = symbols.get(i);

        return ret;
    }

    private DisassemblerSymbol[] getMethods() {
        Vector<DisassemblerSymbol> symbols = new Vector<DisassemblerSymbol>();
        for (DisassemblerSymbol symbol : disassemblerClass.getSymbols())
            if (isMethodItem(symbol) && !isVtable(symbol) && hasItemInList(symbols, symbol))
                symbols.addElement(symbol);
        if (symbols.isEmpty())
            return null;
        symbols = moveConOrDesToStart(symbols);
        DisassemblerSymbol[] ret = new DisassemblerSymbol[symbols.size()];
        for (int i = 0; i < symbols.size(); ++i)
            ret[i] = symbols.get(i);
        return ret;
    }

    private boolean isVtable(DisassemblerSymbol sym) {
        if (vtable == null)
            return false;
        for (DisassemblerSymbol symbol : vtable.getVtables())
            if (symbol.getDemangledName().equals(sym.getDemangledName()))
                return true;
        return false;
    }

    private Vector<DisassemblerSymbol> moveConOrDesToStart(Vector<DisassemblerSymbol> syms) {
        Vector<DisassemblerSymbol> ret = new Vector<DisassemblerSymbol>();
        for (DisassemblerSymbol sym : syms)
            if (isCon(sym))
                ret.addElement(sym);
        for (DisassemblerSymbol sym : syms)
            if (isDes(sym))
                ret.addElement(sym);
        for (DisassemblerSymbol sym : syms)
            if (!isDes(sym) && !isCon(sym))
                ret.addElement(sym);
        return ret;
    }

    private boolean isCon(DisassemblerSymbol symbol) {
        try {
            String name = symbol.getDemangledName().substring(disassemblerClass.getName().length() + 2);
            if (name.startsWith(className))
                return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isDes(DisassemblerSymbol symbol) {
        try {
            String name = symbol.getDemangledName().substring(disassemblerClass.getName().length() + 2);
            if (name.startsWith("~" + className))
                return true;
        } catch (Exception ignored) {
        }
        return false;
    }
}