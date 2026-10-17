package android.example.judgement.Utils.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "players")
public class Player {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "Name")
    private String name;

    @ColumnInfo(name = "Score")
    private int score = 0;

    @ColumnInfo(name = "dealer")
    private boolean dealer;

    @ColumnInfo(name = "Prediction")
    private int prediction = -1;

    // to cement a prediction
    @ColumnInfo(name = "Cement")
    private boolean cement = false;

    // Result of Current Round
    @ColumnInfo(name = "Result")
    private boolean result = false;

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String Name) {
        this.name = Name;
    }
    public int getScore() {
        return score;
    }
    public void setScore(int score) {
        this.score = score;
    }
    public void addScore(int toAdd) {this.score += toAdd;}
    public boolean getDealer() {
        return dealer;
    }
    public void setDealer(boolean status) {
        this.dealer = status;
    }
    public int getPrediction() {return prediction;}
    public void setPrediction(int prediction) {this.prediction = prediction;}
    public boolean getCement() {return cement;}
    public void setCement(boolean cement) {this.cement = cement;}
    public boolean getResult() {return result;}
    public void setResult(boolean result) {this.result = result;}
}
