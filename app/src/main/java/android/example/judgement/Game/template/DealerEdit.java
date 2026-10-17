package android.example.judgement.Game.template;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.example.judgement.Information.About;
import android.example.judgement.Initialise.TemplateActivity;
import android.example.judgement.R;
import android.example.judgement.Utils.database.AppDatabase;
import android.example.judgement.Utils.database.Player;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import java.util.List;

public class DealerEdit extends TemplateActivity {

    RadioButton selectedRadio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleLegacyBack(); }
        });
        setContentView(R.layout.activity_dealer_edit);
        populateRadioGroup();
    }


    private void populateRadioGroup() {
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
            Player temp = AppDatabase.getAppDatabase(getApplicationContext()).dao().findByName(name);
            if(temp.getDealer()) {
                selectedRadio = rb;
                rb.setChecked(true);
            }
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
            new AlertDialog.Builder(DealerEdit.this)
                    .setTitle("Confirm Dealer")
                    .setMessage("Do you wish to make " + name + " the dealer?")
                    .setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AppDatabase.makeDealerByName(getApplicationContext(), name);
                            dialog.cancel();
                            finish();
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

    private void handleLegacyBack() {
        Toast.makeText(getApplicationContext(), "Cannot go Back at this stage", Toast.LENGTH_SHORT).show();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.empty, menu);
        return true;
    }
}
