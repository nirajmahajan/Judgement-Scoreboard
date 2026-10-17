package android.example.judgement.Initialise;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.example.judgement.Information.About;
import android.example.judgement.Information.help.HelpBase;
import android.example.judgement.R;
import android.example.judgement.Utils.Utils;
import android.example.judgement.Utils.database.AppDatabase;
import android.example.judgement.Utils.log.Utils.Log;
import android.graphics.Color;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

public class TemplateActivity extends AppCompatActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public void setContentView(int layoutResID)
    {
        DrawerLayout fullView = (DrawerLayout) getLayoutInflater().inflate(R.layout.activity_template, null);
        FrameLayout activityContainer = (FrameLayout) fullView.findViewById(R.id.template_activity_content);
        getLayoutInflater().inflate(layoutResID, activityContainer, true);
        super.setContentView(fullView);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (useToolbar())
        {
            setSupportActionBar(toolbar);
            setTitle(setTitle());
            toolbar.setTitleTextColor(Color.WHITE);
        }
        else
        {
            toolbar.setVisibility(View.GONE);
        }
    }
    protected boolean useToolbar() {
        return true;
    }

    protected int useMenu() {return R.menu.main_menu;}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(useMenu(), menu);
        return true;
    }

    public Toolbar getToolbar(){
        Toolbar toolbar = findViewById(R.id.toolbar);
        return toolbar;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_about) {
            Intent intent = new Intent(getApplicationContext(), About.class);
            startActivity(intent);
        }else if (id == R.id.menu_done){
            finish();
        }
        else if (id == R.id.menu_newGame) {
            final Activity context = this;
            new AlertDialog.Builder(context)
                    .setTitle("Confirm Your Actions")
                    .setMessage("Are you sure you want to start a new game?")
                    .setCancelable(false)
                    .setPositiveButton("Restart", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AppDatabase.setAllPredictionsToReset(getApplicationContext());
                            AppDatabase.setAllResultsToFalse(getApplicationContext());
                            AppDatabase.setAllScoresToZero(getApplicationContext());
                            Log.clearEditLog();
                            Log.clearRoundLog();
                            Intent intent = new Intent(context, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            dialog.cancel();
                            startActivity(intent);
                            finish();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {}
                    })
                    .show();
        }
        else if (id == R.id.menu_exit) {
            Utils.quitApp(getApplicationContext(), this);
        }
        else if (id == R.id.menu_help) {
            Intent intent = new Intent(this, HelpBase.class);
            startActivity(intent);
        }


        return super.onOptionsItemSelected(item);
    }

    protected String setTitle() {
        return "Judgement";
    }
}
