package android.example.judgement.Game.template.ScoreboardEdit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.example.judgement.R;
import android.example.judgement.Utils.database.AppDatabase;
import android.example.judgement.Utils.database.Player;
import android.example.judgement.Utils.log.Utils.Log;
import android.graphics.Color;
import android.os.Build;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScoreboardEditAdapter extends ArrayAdapter {
    private final Activity context;
    private final ArrayList<String> names;

    ScoreboardEditAdapter (Activity context, ArrayList<String> names) {
        super(context, R.layout.scoreboard_edit_object, names);
        this.names = names;
        this.context = context;
    }

    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.scoreboard_edit_object, null, true);

        TextView tv_rank = rowView.findViewById(R.id.tv_sce_object_rank);
        TextView tv_name = rowView.findViewById(R.id.tv_sce_object_name);
        TextView tv_score = rowView.findViewById(R.id.tv_sce_object_score);
        final ImageButton edit_button = rowView.findViewById(R.id.b_sce_edit);


        tv_rank.setText(String.valueOf(1+position));
        tv_name.setText(names.get(position));
        tv_score.setText(String.valueOf(AppDatabase.getAppDatabase(context).dao().findByName(names.get(position)).getScore()));

        edit_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editListen((ConstraintLayout) edit_button.getParent());
            }
        });
        return rowView;
    }


    private void editListen(ConstraintLayout v) {
        final EditText et_GetName = new EditText(context);
        et_GetName.setInputType(InputType.TYPE_CLASS_NUMBER);
        et_GetName.setHint("New Score");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            et_GetName.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));
        }
        // get the text views inside the view clicked
        final TextView textView_name = v.findViewById(R.id.tv_sce_object_name);
        // create a new dialogue to get new name
        new AlertDialog.Builder(context)
                .setTitle("Change score of  " + textView_name.getText().toString())
                .setMessage("Please Enter a New Score for " + textView_name.getText().toString())
                .setView(et_GetName)
                .setCancelable(false)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String name = textView_name.getText().toString();
                        String new_score = et_GetName.getText().toString();
                        Player player = AppDatabase.getAppDatabase(context).dao().findByName(name);
                        if (AppDatabase.getAppDatabase(context).dao().findById(player.getId()).getScore() != Integer.parseInt(new_score)) {
                            Log.recordEditScore(name, player.getScore(), Integer.parseInt(new_score));
                            AppDatabase.getAppDatabase(context).dao().delete(player);
                            player.setScore(Integer.parseInt(new_score));
                            AppDatabase.getAppDatabase(context).dao().insertAll(player);
                            Toast.makeText(context, name + "'s score is now " + new_score, Toast.LENGTH_SHORT).show();
                            names.clear();
                            List<Player> initPlayers = AppDatabase.getAppDatabase(context).dao().getAllPlayers();
                            Collections.sort(initPlayers, new Comparator<Player>() {
                                @Override
                                public int compare(Player o1, Player o2) {
                                    if(o1.getScore() < o2.getScore()) {return 1;}
                                    else if (o1.getScore() == o2.getScore()) {return 0;}
                                    else {return -1;}
                                }
                            });
                            for (Player pp: initPlayers) {
                                names.add(pp.getName());
                            }
                            notifyDataSetChanged();
                        } else {
                            Toast.makeText(context, "Entered Score is same as the previous value", Toast.LENGTH_SHORT).show();
                        }
                        dialog.cancel();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }
}