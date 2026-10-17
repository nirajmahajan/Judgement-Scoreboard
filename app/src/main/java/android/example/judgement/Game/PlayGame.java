package android.example.judgement.Game;

import android.content.Intent;
import android.example.judgement.Game.TakeHands.TakeHands;
import android.example.judgement.Utils.database.AppDatabase;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class PlayGame extends AppCompatActivity {

    boolean startFrom0;
    String mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        startFrom0 = getIntent().getExtras().getBoolean("START_FROM_0");
        mode = getIntent().getStringExtra("MODE");
        super.onCreate(savedInstanceState);
        AppDatabase.setAllScoresToZero(getApplicationContext());
        AppDatabase.setAllResultsToFalse(getApplicationContext());
        AppDatabase.setAllPredictionsToReset(getApplicationContext());
        Intent intent = new Intent(getApplicationContext(), TakeHands.class);
        int dealnum;
        if (startFrom0) {
            dealnum = 1;
        }
        else {
            dealnum = 52 / AppDatabase.getAppDatabase(getApplicationContext()).dao().countPlayers();
        }
        intent.putExtra("START_FROM_0", startFrom0);
        intent.putExtra("ROUND_NUMBER", dealnum);
        intent.putExtra("MODE", mode);
        intent.putExtra("STEP", 0);
        startActivity(intent);
    }
}
