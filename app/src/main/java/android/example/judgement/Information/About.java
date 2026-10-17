package android.example.judgement.Information;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.example.judgement.Game.template.GameTemplate;
import android.example.judgement.Initialise.TemplateActivity;
import android.example.judgement.R;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class About extends TemplateActivity {


    private TextView gmail;
    private TextView facebook;
    private TextView github;
    @Override
    protected int useMenu() {
        return R.menu.done;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        gmail = findViewById(R.id.mail);
        facebook = findViewById(R.id.facebook);
        github = findViewById(R.id.github);
        facebook.setVisibility(View.INVISIBLE);
        github.setVisibility(View.INVISIBLE);
    }

    public void goToFb (View view) {
        goToUrl ( "https://www.facebook.com/profile.php?id=100004470198737");
    }

    public void goToGit (View view) {
        goToUrl ( "https://github.com/nirajmahajan");
    }

    private void goToUrl (String url) {
        Uri uriUrl = Uri.parse(url);
        Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
        startActivity(launchBrowser);
    }

    public void sendMail(View view) {
        Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);

        String aEmailList[] = { "nirajm@cse.iitb.ac.in" };

        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, aEmailList);

        emailIntent.setType("text/html");
        emailIntent.setPackage("com.google.android.gm");
        startActivity(Intent.createChooser(emailIntent, "Send mail"));
    }


}
