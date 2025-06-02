package models;

/**
 * 创建每一张扑克牌实例
 */
public class Card implements Comparable<Card> {
    // 花色：方块(0)、梅花(1)、红桃(2)、黑桃(3)
    public static final int DIAMOND = 0;//方块
    public static final int CLUB = 1;//梅花
    public static final int HEART = 2;//红桃
    public static final int SPADE = 3;//黑桃
    
    // 点数：3(0), 4(1), ..., K(10), A(11), 2(12)
    public static final int THREE = 0;
    public static final int FOUR = 1;
    public static final int FIVE = 2;
    public static final int SIX = 3;
    public static final int SEVEN = 4;
    public static final int EIGHT = 5;
    public static final int NINE = 6;
    public static final int TEN = 7;
    public static final int JACK = 8;
    public static final int QUEEN = 9;
    public static final int KING = 10;
    public static final int ACE = 11;
    public static final int TWO = 12;
    
    private int suit; // 花色
    private int rank; // 点数
    private boolean selected; // 是否被选中
    
    //点数和花色，是否被选中，三个属性构成一张牌
    public Card(int suit, int rank) {
        this.suit = suit;
        this.rank = rank;
        this.selected = false;
    }
    
    //----------------辅助函数---------------------

    //获取花色
    public int getSuit() {
        return suit;
    }

    //获取点数
    public int getRank() {
        return rank;
    }

    //获取是否被选中
    public boolean isSelected() {
        return selected;
    }

    //设置是否被选中
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    /**
     * 获取卡牌的显示名称
     */
    public String getDisplayName() {
        String suitStr;
        switch (suit) {
            case DIAMOND: suitStr = "♦"; break;
            case CLUB: suitStr = "♣"; break;
            case HEART: suitStr = "♥"; break;
            case SPADE: suitStr = "♠"; break;
            default: suitStr = "系统错误，未知的牌花色";
        }
        
        String rankStr;
        switch (rank) {
            case THREE: rankStr = "3"; break;
            case FOUR: rankStr = "4"; break;
            case FIVE: rankStr = "5"; break;
            case SIX: rankStr = "6"; break;
            case SEVEN: rankStr = "7"; break;
            case EIGHT: rankStr = "8"; break;
            case NINE: rankStr = "9"; break;
            case TEN: rankStr = "10"; break;
            case JACK: rankStr = "J"; break;
            case QUEEN: rankStr = "Q"; break;
            case KING: rankStr = "K"; break;
            case ACE: rankStr = "A"; break;
            case TWO: rankStr = "2"; break;
            default: rankStr = "系统错误，未知的牌点数";
        }
        
        return suitStr + rankStr;
    }
    
    /**
     * 比较两张卡牌的大小，获取差值，用于排序
     * 该方法用于系统内部排序，不涉及游戏规则的比较逻辑
     */
    @Override
    public int compareTo(Card other) {
        if (this.rank != other.rank) {
            return this.rank - other.rank;
        } else {
            return this.suit - other.suit;
        }
    }
    
    /**
     * 重写toString方法，显示卡牌的可读形式
     */
    @Override
    public String toString() {
        return getDisplayName();
    }

}