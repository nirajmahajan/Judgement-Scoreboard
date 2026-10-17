package android.example.judgement.Initialise;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.example.judgement.Information.About;
import android.example.judgement.Information.help.HelpBase;
import android.example.judgement.R;
import android.example.judgement.Utils.Utils;
import android.example.judgement.Utils.database.AppDatabase;
import android.example.judgement.Utils.database.Player;
import android.example.judgement.Utils.log.Utils.Log;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class Init_Players extends AppCompatActivity {

    private ListView listView;
    private ArrayList<String> names;
    InitAdapter adapter;
    String mode;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_init__players);
        Toolbar toolbar = findViewById(R.id.toolbar_init);
        setSupportActionBar(toolbar);

        mode = getIntent().getStringExtra("MODE");
        listView = findViewById(R.id.lv_init_players);
        names = new ArrayList<>();
        if (AppDatabase.getAppDatabase(getApplicationContext()).dao().countPlayers() != 0) {
            List<Player> initPlayers = AppDatabase.getAppDatabase(getApplicationContext()).dao().getAllPlayers();
            for (Player player: initPlayers) {
                names.add(player.getName());
            }
        }

        // make an adapter
        adapter = new InitAdapter(this, names);

        listView.setAdapter(adapter);
     }

    public void addPlayers(final View view) {
        // create edit text for dialogbox
        final EditText et_GetName = new EditText(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            et_GetName.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));
        }
        final Context ctx = getApplicationContext();
        final LayoutInflater inflater = this.getLayoutInflater();

        et_GetName.setHint("Name");
        new AlertDialog.Builder(this)
                .setTitle("Add a Player")
                .setMessage("Please Enter the Player's Name")
                .setView(et_GetName)
                .setCancelable(false)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int initialCount = AppDatabase.getAppDatabase(ctx).dao().countPlayers();
                        final String name = et_GetName.getText().toString();
                        AppDatabase.verifiedAddPlayer(ctx, name);
                        int finalCount = AppDatabase.getAppDatabase(ctx).dao().countPlayers();
                        dialog.cancel();
                        if(initialCount != finalCount) {
                            names.add(name);
                            adapter.notifyDataSetChanged();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.init_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_about) {
            Intent intent = new Intent(getApplicationContext(), About.class);
            startActivity(intent);
        }
        else if (id == R.id.menu_startGame) {
            if (2 > AppDatabase.getAppDatabase(this).dao().countPlayers()){
                Toast.makeText(getApplicationContext(), "Need at least Two Players for the Game!", Toast.LENGTH_SHORT).show();
            }
            else {
                AppDatabase.getAppDatabase(getApplicationContext()).dao().getAllPlayers().get(0).setDealer(true);
                AppDatabase.normalizeIDs(getApplicationContext());
                Log.clearEditLog();
                Log.clearRoundLog();
                Intent intent = new Intent(this, Init_Dealer.class);
                intent.putExtra("MODE", mode);
                startActivity(intent);
            }
        }
        else if (id == R.id.int_menu_exit) {
            Utils.quitApp(getApplicationContext(), this);
        } else if (id == R.id.menu_help) {
            Intent intent = new Intent(this, HelpBase.class);
            startActivity(intent);
        }


        return super.onOptionsItemSelected(item);
    }

}
