package android.example.judgement.Initialise;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.example.judgement.Game.PlayGame;
import android.example.judgement.R;
import android.example.judgement.Utils.database.AppDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.util.List;

public class Init_Dealer extends TemplateActivity {

    String mode;

    boolean startFrom0 = true;
    RadioButton selectedRadio = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleLegacyBack(); }
        });
        setContentView(R.layout.activity_init__dealer);
        populateRadioGroup();
        mode = getIntent().getStringExtra("MODE");
        askForStart();
    }

    private void handleLegacyBack() {
        Toast.makeText(getApplicationContext(), "Cannot go Back at this stage", Toast.LENGTH_SHORT).show();
    }

    private void populateRadioGroup(){
        RadioGroup radioGroup = findViewById(R.id.rg_dealers);
        List<String> names = AppDatabase.getAllNames(getApplicationContext());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        int i = 0;
        for (String name : names) {
            RadioButton rb = new RadioButton(this);
            rb.setText(name);
            rb.setTextSize(40);
            rb.setId(i);
            rb.setOnClickListener(onClickRadio);
            i++;
            radioGroup.addView(rb, p);
        }
    }

    private View.OnClickListener onClickRadio = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            selectedRadio = ((RadioButton) v);
        }
    };

    public void makeDealerAndGo(View v) {
        if (selectedRadio == null) {
            Toast.makeText(getApplicationContext(), "Select a Player First", Toast.LENGTH_SHORT).show();
        }
        else {
            final String name = selectedRadio.getText().toString();
            new AlertDialog.Builder(Init_Dealer.this)
                    .setTitle("Confirm Dealer")
                    .setMessage("Do you wish to make " + name + " the dealer?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AppDatabase.makeDealerByName(getApplicationContext(), name);
                            dialog.cancel();
                            Intent intent = new Intent(getApplicationContext(), PlayGame.class);
                            intent.putExtra("START_FROM_0", startFrom0);
                            intent.putExtra("MODE", mode);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .show();
        }
    }

    private void askForStart() {

        int end  = 52 / AppDatabase.getAppDatabase(getApplicationContext()).dao().countPlayers();
        new AlertDialog.Builder(this)
                .setTitle("How do you wish to start")
                .setMessage("Do you wish to start dealing from 1 or " + String.valueOf(end) + " ?")
                .setCancelable(false)
                .setPositiveButton("Start from '1'", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startFrom0 = true;
                        dialog.cancel();
                    }
                })
                .setNegativeButton("Start from '" + end + "'", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startFrom0 = false;
                        dialog.cancel();
                    }
                })
                .show();

    }
}
