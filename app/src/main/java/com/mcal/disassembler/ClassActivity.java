package com.mcal.disassembler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gc.materialdesign.widgets.SnackBar;
import com.mcal.disassembler.nativeapi.DisassemblerClass;
import com.mcal.disassembler.nativeapi.DisassemblerSymbol;
import com.mcal.disassembler.nativeapi.DisassemblerVtable;
import com.mcal.disassembler.nativeapi.Dumper;
import com.mcal.disassembler.util.ClassGeter;
import com.mcal.disassembler.util.FileSaver;
import com.mcal.disassembler.util.HeaderGenerator;
import com.mcal.disassembler.vtable.VtableDumper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassActivity extends AppCompatActivity {
    com.gc.materialdesign.widgets.ProgressDialog dialog;
    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    showSavingProgressDialog();
                    break;
                case 1:
                    new SnackBar(ClassActivity.this, ClassActivity.this.getString(R.string.done)).show();
                    dismissProgressDialog();
                    break;
            }
        }
    };
    private String path;
    private String name;
    private List<Map<String, Object>> data;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.class_activity);
        name = getIntent().getExtras().getString("name");
        path = getIntent().getExtras().getString("path");

        ListView list = findViewById(R.id.class_activity_list_view);
        data = getData();
        SymbolsAdapter adapter = new SymbolsAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new ItemClickListener());

        setTitle(name);
        //TextView title=(TextView) findViewById(R.id.classactivityTextViewName);
        //title.setText(name);

        if (hasVtable()) {
            findViewById(R.id.classactivityTextViewButtonFloatVtable).setVisibility(View.VISIBLE);
            findViewById(R.id.classactivityButtonFloat).setVisibility(View.VISIBLE);
        }
    }

    private List<Map<String, Object>> getData() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> map;
        DisassemblerClass classThis = findClass();
        if (classThis == null)
            return list;
        for (int i = 0; i < classThis.getSymbols().size(); ++i) {
            map = new HashMap<>();
            if (classThis.getSymbols().get(i).getType() == 1)
                map.put("img", R.drawable.box_blue);
            else if (classThis.getSymbols().get(i).getType() == 2)
                map.put("img", R.drawable.box_red);
            else map.put("img", R.drawable.box_pink);
            map.put("title", classThis.getSymbols().get(i).getDemangledName());
            map.put("info", classThis.getSymbols().get(i).getName());
            map.put("type", classThis.getSymbols().get(i).getType());
            list.add(map);
        }
        return list;
    }

    public void save(View view) {
        new Thread() {
            public void run() {
                mHandler.sendEmptyMessage(0);
                HeaderGenerator generator = new HeaderGenerator(findClass(), findVtable(), path);
                FileSaver saver = new FileSaver(Environment.getExternalStorageDirectory().toString() + "/Disassembler/headers/", getSaveName(name), generator.generate());
                saver.save();
                mHandler.sendEmptyMessage(1);
            }
        }.start();
    }

    private DisassemblerClass findClass() {
        for (DisassemblerClass clasz : Dumper.classes)
            if (clasz.getName().equals(name))
                return clasz;
        return ClassGeter.getClass(name);
    }

    private DisassemblerVtable findVtable() {
        for (DisassemblerVtable clasz : Dumper.exploed)
            if (clasz.getName().equals(getZTVName(name)))
                return clasz;
        return VtableDumper.dump(path, getZTVName(name));
    }

    private String getZTVName(String mangledName) {
        StringBuilder ret = new StringBuilder("_ZTV");
        String[] names = mangledName.split("::");
        for (String str : names)
            ret.append(str.length()).append(str);
        return ret.toString();
    }

    private String getSaveName(String mangledName) {
        StringBuilder ret = new StringBuilder();
        String[] names = mangledName.split("::");
        boolean isFirstName = true;
        for (String str : names) {
            if (isFirstName) {
                ret.append(str);
                isFirstName = false;
            } else
                ret.append("$").append(str);
        }
        return ret + ".h";
    }

    public void toVtableActivity(View view) {
        showLoadingProgressDialog();
        new Thread() {
            public void run() {
                DisassemblerVtable vtable = VtableDumper.dump(path, getZTVName(name));
                if (vtable != null)
                    ClassActivity.this.toVtableActivity_(vtable);
                dismissProgressDialog();
            }
        }.start();
    }

    private boolean hasVtable() {
        String vtableThis = getZTVName(name);
        for (DisassemblerSymbol symbol : Dumper.symbols)
            if (symbol.getName().equals(vtableThis))
                return true;
        return false;
    }

    public void toVtableActivity_(DisassemblerVtable vtable) {
        Bundle bundle = new Bundle();
        bundle.putString("name", getZTVName(name));
        bundle.putString("path", path);
        Dumper.exploed.addElement(vtable);
        Intent intent = new Intent(this, VtableActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    public void showLoadingProgressDialog() {
        dialog = new com.gc.materialdesign.widgets.ProgressDialog(this, getString(R.string.loading));
        dialog.show();
    }

    public void showSavingProgressDialog() {
        dialog = new com.gc.materialdesign.widgets.ProgressDialog(this, getString(R.string.saving));
        dialog.show();
    }

    public void dismissProgressDialog() {
        if (dialog != null)
            dialog.dismiss();
        dialog = null;
    }

    static class ViewHolder {
        ImageView img;
        TextView title;
        public TextView info;
        public int type;
    }

    private final class ItemClickListener implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> arg0, View view, int arg2, long arg3) {
            Bundle bundle = new Bundle();
            bundle.putString("demangledName", (String) (((ViewHolder) view.getTag()).title.getText()));
            bundle.putString("name", (String) (((ViewHolder) view.getTag()).info.getText()));
            bundle.putInt("type", ((ViewHolder) view.getTag()).type);
            bundle.putString("filePath", path);
            Intent intent = new Intent(ClassActivity.this, SymbolActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);
        }
    }

    public class SymbolsAdapter extends BaseAdapter {
        private LayoutInflater mInflater = null;

        private SymbolsAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.symbol_list_item, null);
                holder.img = convertView.findViewById(R.id.symbolslistitemimg);
                holder.title = convertView.findViewById(R.id.symbolslistitemTextViewtop);
                holder.info = convertView.findViewById(R.id.symbolslistitemTextViewbottom);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.img.setBackgroundResource((Integer) data.get(position).get("img"));
            holder.title.setText((String) data.get(position).get("title"));
            holder.info.setText((String) data.get(position).get("info"));
            holder.type = ((int) data.get(position).get("type"));

            return convertView;
        }
    }
}
