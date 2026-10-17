package android.example.judgement.Game.TakeHands;

import android.content.Intent;
import android.example.judgement.Game.ShowFinalScore.DisplayFinalScore;
import android.example.judgement.Game.TakeResults.TakeResults;
import android.example.judgement.Game.template.GameTemplate;
import android.example.judgement.R;
import android.example.judgement.Utils.database.AppDatabase;
import android.example.judgement.Utils.database.Player;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TakeHands extends GameTemplate {

    private ListView listView;
    private TextView title;
    private ArrayList<String> names;
    TakeHandsAdapter adapter;
    private int round;
    private boolean startFrom0;
    FloatingActionButton fab;
    String mode;
    int step;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleLegacyBack(); }
        });
        setContentView(R.layout.activity_take_hands);

        step = getIntent().getExtras().getInt("STEP");
        mode = getIntent().getStringExtra("MODE");
        setToolbarTitle("Taking Hands");
        Intent intent = getIntent();
        round = intent.getExtras().getInt("ROUND_NUMBER");
        startFrom0 = intent.getExtras().getBoolean("START_FROM_0");
        title = findViewById(R.id.tv_take_title);
        if (round == 1) {
            title.setText("Distribute " + round + " card each");
        }
        else {
            title.setText("Distribute " + round + " cards each");
        }

        if (mode.equals("FIXED")) {
            title.setGravity(Gravity.LEFT);
            title.setTextSize(25);
            title.append("\nCurrent Trump is " + getTrump());
        }



        fab = findViewById(R.id.fab_take_go);
        fab.setVisibility(View.INVISIBLE);
        listView = findViewById(R.id.lv_take_players);
        names = new ArrayList<>();
        if (AppDatabase.getAppDatabase(getApplicationContext()).dao().countPlayers() != 0) {
            List<Player> initPlayers = AppDatabase.getAppDatabase(getApplicationContext()).dao().getAllPlayers();
            for (Player player: initPlayers) {
                names.add(player.getName());
            }
        }
        int count = AppDatabase.getAppDatabase(getApplicationContext()).dao().countPlayers();
        Player dealer = AppDatabase.getAppDatabase(getApplicationContext()).dao().findDealer(true);
        Collections.rotate(names, count - dealer.getId());
        // make an adapter
        adapter = new TakeHandsAdapter(this, names, round, fab);
        listView.setAdapter(adapter);
    }


    private void handleLegacyBack() {
        Toast.makeText(getApplicationContext(), "Cannot go Back at this stage", Toast.LENGTH_SHORT).show();
    }

    public void verifiedProceed(View view) {
        boolean all_filled = true;
        for (String name : names) {
            all_filled = all_filled && (AppDatabase.getAppDatabase(getApplicationContext()).dao().findByName(name).getPrediction() != -1);
        }
        if (all_filled){
            Intent allow = new Intent(getApplicationContext(), TakeResults.class);
            allow.putExtra("START_FROM_0", startFrom0);
            allow.putExtra("ROUND_NUMBER", round);
            allow.putExtra("STEP", step);
            allow.putExtra("MODE", mode);
            startActivity(allow);
            finish();
        }
        else {
            Toast.makeText(getApplicationContext(), "Please input all data before proceeding!", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void restartLevel() {
        AppDatabase.setAllResultsToFalse(getApplicationContext());
        AppDatabase.setAllPredictionsToReset(getApplicationContext());
        Intent allow = new Intent(getApplicationContext(), TakeHands.class);
        allow.putExtra("START_FROM_0", startFrom0);
        allow.putExtra("ROUND_NUMBER", round);
        allow.putExtra("MODE", mode);
        allow.putExtra("STEP", step);
        startActivity(allow);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int new_count = AppDatabase.getAllNames(getApplicationContext()).size();
        int new_round_limit = 52 / new_count;
        if (startFrom0 && new_round_limit < round) {
            Toast.makeText(getApplicationContext(), new_count + " people cannot play the " + round + "th round!\n Exiting!", Toast.LENGTH_LONG).show();
            Intent exit = new Intent(getApplicationContext(), DisplayFinalScore.class);
            startActivity(exit);
            finish();
        }
        else if (!startFrom0 && round > new_round_limit) {
            Toast.makeText(getApplicationContext(), new_count + " people cannot play the " + round + "th round!\nJumping Down to the " + String.valueOf(new_round_limit) + "th round!", Toast.LENGTH_LONG).show();
            Intent jumpDown = new Intent(getApplicationContext(), TakeHands.class);
            jumpDown.putExtra("START_FROM_0", startFrom0);
            jumpDown.putExtra("ROUND_NUMBER", new_round_limit);
            jumpDown.putExtra("MODE", mode);
            jumpDown.putExtra("STEP", step);
            startActivity(jumpDown);
            finish();
        }
        else {
            names = new ArrayList<>();
            if (AppDatabase.getAppDatabase(getApplicationContext()).dao().countPlayers() != 0) {
                List<Player> initPlayers = AppDatabase.getAppDatabase(getApplicationContext()).dao().getAllPlayers();
                for (Player player: initPlayers) {
                    names.add(player.getName());
                }
            }
            int count = AppDatabase.getAppDatabase(getApplicationContext()).dao().countPlayers();
            Player dealer = AppDatabase.getAppDatabase(getApplicationContext()).dao().findDealer(true);
            Collections.rotate(names, count - dealer.getId());
            adapter = new TakeHandsAdapter(this, names, round, fab);
            listView.setAdapter(adapter);adapter.notifyDataSetChanged();
        }
    }

    private String getTrump() {
        int rem = step % 4;
        if(rem == 0) {
            return "Spades";
        } else if (rem == 1) {
            return "Diamonds";
        } else if (rem == 2) {
            return "Clubs";
        } else {
            return "Hearts";
        }
    }

}