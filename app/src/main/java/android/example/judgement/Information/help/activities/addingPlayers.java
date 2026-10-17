package android.example.judgement.Information.help.activities;

import android.example.judgement.Information.help.HelpTemplate;
import android.example.judgement.R;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class addingPlayers extends HelpTemplate {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected Class prevActivity() {
        return chooseMode.class;
    }

    @Override
    protected Class nextActivity() {
        return settingDealer.class;
    }

    @Override
    protected int image() {
        return R.drawable.add_players;
    }

    String description = "Click on the floating button on the bottom corner to add new players\nPlease add the players in the sequence in which they are sitting, since the round progresses in a clockwise manner\n\nYou have to add at least two players to start the game.\n You also cannot add players having the same name\n\nClick on the 'pen icon' next to a player's name to edit his/her name, and on the 'bin' icon to delete the player.\n\nClick on 'Start Game' When ready.";

    @Override
    protected String description() {
        return description;
    }

    @Override
    protected String pageTitle() {
        return "Adding Players";
    }
}
