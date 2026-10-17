package android.example.judgement.Initialise;

import android.content.Intent;
import android.example.judgement.Information.About;
import android.example.judgement.Information.FixedVsCustom;
import android.example.judgement.Information.help.HelpBase;
import android.example.judgement.R;
import android.example.judgement.Utils.database.AppDatabase;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    RadioGroup rg_main;
    RadioButton rb_custom;
    RadioButton rb_fixed;
    TextView tv_need_help;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("Judgement");
        toolbar.setTitleTextColor(Color.WHITE);
        tv_need_help = findViewById(R.id.main_fixed_vs_custom);
        tv_need_help.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), FixedVsCustom.class);
                startActivity(intent);
            }
        });
        rg_main = findViewById(R.id.rg_main);
        rb_custom = findViewById(R.id.rb_main_custom_trump);
        rb_fixed = findViewById(R.id.rb_main_fixed_trump);
    }

    @Override
    protected void onDestroy() {
        // just comment out this override to maintain the database after 'destroy'
//        AppDatabase.destroyInstance(getApplicationContext());
        super.onDestroy();
    }

    public void goToPlayerInit(View view) {
        if (!rb_fixed.isChecked() && !rb_custom.isChecked()) {
            Toast.makeText(getApplicationContext(), "Please select a mode", Toast.LENGTH_LONG).show();
        } else {
            String mode;
            if(rb_fixed.isChecked()) {
                mode = "FIXED";
            }
            else {
                mode = "CUSTOM";
            }

            Intent intent = new Intent(this.getApplicationContext(), Init_Players.class);
            intent.putExtra("MODE", mode);
            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.welcome_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_about) {
            Intent intent = new Intent(getApplicationContext(), About.class);
            startActivity(intent);
        }

        else if (id == R.id.menu_exit) {
            AppDatabase.getAppDatabase(this).dao().purge();
            moveTaskToBack(true);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }
        else if (id == R.id.menu_help) {
            Intent intent = new Intent(getApplicationContext(), HelpBase.class);
            startActivity(intent);
        }


        return super.onOptionsItemSelected(item);
    }


}
