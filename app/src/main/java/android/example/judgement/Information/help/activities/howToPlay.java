package android.example.judgement.Information.help.activities;

import android.example.judgement.Information.help.HelpTemplate;
import android.example.judgement.Initialise.TemplateActivity;
import android.example.judgement.R;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class howToPlay extends TemplateActivity {

    TextView tv_howToPlay;
    TextView tv_playRound;
    TextView tv_hierarchy;

    @Override
    protected int useMenu() {
        return R.menu.about_exit_done;
    }

    @Override
    protected String setTitle() {
        return "Help Centre";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_how_to_play);
        tv_howToPlay = findViewById(R.id.tv_help_how_to_play);
        tv_playRound = findViewById(R.id.tv_help_how_to_play_round);
        tv_hierarchy = findViewById(R.id.tv_help_how_to_play_hierarchy);
        writeText();
    }

    private void writeText() {
        tv_howToPlay.setText("Judgment is a popular Indian game where you have to predict the number of hands you'll make and try to make those number of hands that you predict.\nIt is a game usually for 3 or more players.  Even 2 players can play but then the game won't be that interesting.\n\nThe maximum number of cards that can be distributed among the players depends on the number of players. For example, in case of 8 players, the maximum number of cards that can be distributed are 6.\nThe game can start either from a single card, or from the maximum number of cards that can be distributed\n\n");
        tv_playRound.setText("After the cards are dealt, the player who got the 1st card first will be the first to predict the hands that he will make after looking at the cards. \n\nAll the other players, at their turn (in counterclockwise direction) make their predictions on the number of hands they will make. After the predictions are over, the game begins with player who was dealt the 1st card first playing first, if the game is being played with fixed trumps.\nIn case of custom trumps, the player with the maximum prediction, who decides the trump, starts the round\n\n");
        tv_hierarchy.setText("The Ace carries the maximum score, followed by King, then Queen, then Jack, then a ten and so on up to the two which has the least score.\n\nThe one who makes a hand gets the next turn.\n\nThe starting player can introduce any suit for the first hand.\nA player has to play the introduced suit, if he possesses a card of the same suit. If not, he can either use a trump card, to win the hand, or he can leave any card of any other suit.\n Trump over-rules all the other suits.\n\nAfter the round is over, all winners get 10 points, in addition to the hands they predicted, while the losers get zero points.\nFor example, if player-1 predicted 5 hands, player-2 predicted 3 hands, and, player-1 lost, while player-2 won. Then player-1 won't get any points while player-2 will get 12 points\nIf a player has predicted zero hands and does not make any hand, then he gets ten points.\n\n\n\n\n\n");
    }
}
