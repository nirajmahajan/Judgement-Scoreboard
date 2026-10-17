package android.example.judgement.Information.help;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.example.judgement.Information.About;
import android.example.judgement.Initialise.MainActivity;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.Inet4Address;

public class HelpTemplate extends AppCompatActivity {

    private Button prev;
    private Button next;
    private ImageView screenshot;
    private TextView tv_description;
    private TextView title;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_template);
        followUp();
    }


    private  void followUp()
    {
        DrawerLayout fullView = (DrawerLayout) getLayoutInflater().inflate(R.layout.activity_help_template, null);
        View parent = fullView.getRootView();
        parent.setBackgroundColor(getResources().getColor(backgroundColor()));

        FrameLayout activityContainer = (FrameLayout) fullView.findViewById(R.id.help_template_activity_content);
        super.setContentView(fullView);
        prev = findViewById(R.id.help_prev);
        next = findViewById(R.id.help_next);
        screenshot = findViewById(R.id.iv_help_template_screenshot);
        tv_description = findViewById(R.id.tv_help_template_describe);
        title = findViewById(R.id.help_title_page);

        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), prevActivity());
                startActivity(intent);
                finish();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), nextActivity());
                startActivity(intent);
                finish();
            }
        });

        if(hideNextButton()) {
            next.setVisibility(View.INVISIBLE);
        }

        if(hidePrevButton()) {
            prev.setVisibility(View.INVISIBLE);
        }

        screenshot.setImageDrawable(getResources().getDrawable(image()));
        tv_description.setText(description());
        title.setText(pageTitle());

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (useToolbar())
        {
            setSupportActionBar(toolbar);
            setTitle("Help Centre");
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

    protected int useMenu() {return R.menu.about_exit_done;}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(useMenu(), menu);
        return true;
    }

    public Toolbar getToolbar(){
        Toolbar toolbar = findViewById(R.id.toolbar);
        return toolbar;
    }

    protected boolean hidePrevButton() {return false;}
    protected boolean hideNextButton() {return false;}

    protected java.lang.Class nextActivity() {return MainActivity.class;}
    protected java.lang.Class prevActivity() {return MainActivity.class;}

    protected String description() {return "Description Here\n\n\n";}
    protected int image() {return R.drawable.ic_launcher_foreground;}
    protected String pageTitle() {return "The title of this page is abcd";}

    protected int backgroundColor(){return R.color.defaultBackground;};

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


        return super.onOptionsItemSelected(item);
    }
}
