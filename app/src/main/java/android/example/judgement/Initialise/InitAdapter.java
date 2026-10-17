package android.example.judgement.Initialise;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.example.judgement.R;
import android.example.judgement.Utils.database.AppDatabase;
import android.example.judgement.Utils.database.Player;
import android.graphics.Color;
import android.os.Build;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;

public class InitAdapter extends ArrayAdapter {
    private final Activity context;
    ArrayList<String> names;

    public InitAdapter(Activity context, ArrayList<String> names) {
        super(context, R.layout.init_object, names);
        this.names = names;
        this.context = context;
    }

    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.init_object, null, true);

        TextView tv_id = rowView.findViewById(R.id.tv_id_init);
        TextView tv_name = rowView.findViewById(R.id.tv_init_name);
        final ImageButton edit_button = rowView.findViewById(R.id.b_edit_init);
        final ImageButton delete_button = rowView.findViewById(R.id.b_delete_init);

        tv_id.setText(String.valueOf(1+position));
        tv_name.setText(names.get(position));

        edit_button.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    editListen((ConstraintLayout) edit_button.getParent());
                                }
                            });
        delete_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConstraintLayout ll = (ConstraintLayout) delete_button.getParent();
                TextView txt = ll.findViewById(R.id.tv_init_name);
                String name = txt.getText().toString();
                Player player = AppDatabase.getAppDatabase(context).dao().findByName(name);
                AppDatabase.getAppDatabase(context).dao().delete(player);
                names.remove(name);
                notifyDataSetChanged();
            }
        });

        return rowView;
    }


    private void editListen(ConstraintLayout v) {
        final EditText et_GetName = new EditText(context);
        et_GetName.setHint("Name");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            et_GetName.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));
        }
        // get the text views inside the view clicked
        TextView textView_id = v.findViewById(R.id.tv_id_init);
        final TextView textView_name = v.findViewById(R.id.tv_init_name);
        et_GetName.setText(textView_name.getText().toString());
        final int old_id =  Integer.valueOf(textView_id.getText().toString());
        // create a new dialogue to get new name
        new AlertDialog.Builder(context)
                .setTitle("Rename " + textView_name.getText().toString())
                .setMessage("Please Enter a New Name")
                .setView(et_GetName)
                .setCancelable(false)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String name = textView_name.getText().toString();
                        String new_name = et_GetName.getText().toString();
                        Player player = AppDatabase.getAppDatabase(context).dao().findByName(name);
                        AppDatabase.verifiedRename(context, player, new_name);
                        if (AppDatabase.getAppDatabase(context).dao().findById(player.getId()).getName().equals(new_name)) {
                            textView_name.setText(new_name);
                            names.set(names.indexOf(name), new_name);
                            notifyDataSetChanged();
                        }
                        dialog.cancel();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .show();
    }
}
