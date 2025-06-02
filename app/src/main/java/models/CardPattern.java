package models;

import java.util.List;

//实现牌型的识别和比较

/**
 * 表示一种牌型（单张、对子、顺子等）
 */
public class CardPattern {
    // 牌型类型
    public static final int INVALID = 0;       // 无效
    public static final int SINGLE = 1;        // 单张
    public static final int PAIR = 2;          // 对子
    public static final int STRAIGHT = 3;      // 顺子（五张），五张连续点数的牌(A可以作为K后面的牌)
    public static final int  FLUSH= 4;          // 同花，五张相同花色但不是连续点数的牌
    public static final int THREE_WITH_PAIR = 5; // 三带二，三张点数相同的牌加一个对子
    public static final int BOMB = 6;          // 炸弹（四带一），可以打除了同花顺以外的任何牌型，这个牌型可以打对子和单张
    public static final int FLUSH_STRAIGHT = 7; // 同花顺（五张），同一花色的顺子，这个牌型可以打对子和单张

    private int patternType;      // 牌型类型
    private List<Card> cards;     // 牌型包含的牌
    private Card highestCard;     // 牌型中最大的牌
    
    /**
     * 创建一个牌型实例
     * @param patternType 牌型类型
     * @param cards 包含的牌
     * @param highestCard 牌型中最大的牌
     */
    public CardPattern(int patternType, List<Card> cards, Card highestCard) {
        this.patternType = patternType;
        this.cards = cards;
        this.highestCard = highestCard;
    }
    
    /**
     * 获取牌型类型
     */
    public int getPatternType() {
        return patternType;
    }
    
    /**
     * 获取牌型包含的牌
     */
    public List<Card> getCards() {
        return cards;
    }
    
    /**
     * 获取牌型中最大的牌
     */
    public Card getHighestCard() {
        return highestCard;
    }
    
    /**
     * 判断此牌型是否能够打过另一个牌型
     * 前提条件：两个牌型都必须是有效的
     * @param other 另一个牌型
     * @return 是否能打过
     * @throws IllegalArgumentException 如果输入的牌型无效
     */
    public boolean canBeat(CardPattern other) {
        // 验证前置条件
        if (patternType == INVALID || other.patternType == INVALID) {
            throw new IllegalArgumentException("比较的牌型必须有效");
        }
        

        //规则1：比较单张牌的牌型
        if(patternType == SINGLE && other.patternType == SINGLE){
            //单张能打出去的条件是：
            //1.对面是单张
            //2.我的单张点数最大的牌比对面单张的点数最大的牌大，点数相同，则比较花色
            if(highestCard.getRank() > other.highestCard.getRank()){
                return true;
            }else if(highestCard.getRank() == other.highestCard.getRank()){
                if(highestCard.getSuit() > other.highestCard.getSuit()){
                    return true;
                }else{
                    return false;
                }
            }
            return false;
        }

        //规则2：比较对子的牌型
        if(patternType == PAIR && other.patternType == PAIR){
            //对子能打出去的条件是：
            //1.对面是对子
            //2.我的对子点数最大的牌比对面单张的点数最大的牌大，点数相同，则比较花色
            if(highestCard.getRank() > other.highestCard.getRank()){
                return true;
            }else if(highestCard.getRank() == other.highestCard.getRank()){
                if(highestCard.getSuit() > other.highestCard.getSuit()){
                    return true;
                }else{
                    return false;
                }
            }
            return false;
        }


        //规则3：比较顺子的牌型
        if(patternType == STRAIGHT && other.patternType == STRAIGHT){
            //顺子能打出去的条件是：
            //1.对面是顺子
            //2.我的顺子点数最大的牌比对面顺子的点数最大的牌大 或者 我的顺子点数最大的牌比对面顺子的点数最大的牌点数相同，但是我的顺子最大牌的花色比对面顺子的最大牌的花色大
            if(highestCard.getRank() > other.highestCard.getRank()){
                return true;
            }else if(highestCard.getRank() == other.highestCard.getRank()){
                if(highestCard.getSuit() > other.highestCard.getSuit()){
                    return true;
                }else{
                    return false;
                }
            }
            return false;
        }

        //规则4：比较同花的牌型
        if(patternType == FLUSH && (other.patternType == FLUSH || other.patternType == STRAIGHT)){
            //同花能打出去的条件是：
            //1.对面是顺子或者同花
            //2.我的同花最大牌的花色比对面同花的最大牌的花色大，或者，我的同花最大牌的花色和对面同花的最大牌的花色相同，我的同花点数最大的牌比对面同花的点数最大的牌大
            if (other.patternType == STRAIGHT){
                return true;
            }else{
                if (highestCard.getSuit() > other.highestCard.getSuit()){
                    return true;
                }else if (highestCard.getSuit() == other.highestCard.getSuit()){
                    if (highestCard.getRank() > other.highestCard.getRank()){
                        return true;
                    }else{
                        return false;
                    }
                }else{
                    return false;
                }
            }
        }


        //规则5：比较三带二的牌型
        if(patternType == THREE_WITH_PAIR && (other.patternType == THREE_WITH_PAIR || other.patternType == STRAIGHT || other.patternType == FLUSH)){
            //三带二能打出去的条件是：
            //1.对面是顺子，同花或者三带二
            //2.三带二大于顺子和同花，并且，不需要任何条件，如果对面是三带二，我方是三带二，则比较三张牌中最大的牌的点数
            if(other.patternType == STRAIGHT || other.patternType == FLUSH){
                return true;
            }else{
                if (highestCard.getRank() > other.highestCard.getRank()){
                    return true;
                }else{
                    return false;
                }
            }
        }
        
        //规则6：比较炸弹的牌型
        if(patternType == BOMB && other.patternType != FLUSH_STRAIGHT){
            //炸弹能打出去的条件是：
            //1.对面是除了同花顺以外的任何牌型
            //2.炸弹大于同花顺和炸弹以外的任何牌型，并且，不需要任何条件，如果对面是炸弹，我方是炸弹，则比较四张牌中最大的牌的点数
           if(other.patternType != BOMB){
                return true;
           }else{
                if (highestCard.getRank() > other.highestCard.getRank()){
                    return true;
                }else{
                    return false;
                }
           }
        }
        
        // 规则7: 同花顺 > 任何其他牌型
        if (patternType == FLUSH_STRAIGHT) {
            //同花顺能打出去的条件是：
            //1.对面是任何牌型
            //2.同花顺大于除了同花顺以外的任何牌型，并且，不需要任何条件，如果对面是同花顺，我方是同花顺，则比较五张牌中最大的牌的花色，如果花色相同，则比较五张牌中最大的牌的点数
            if(other.patternType != FLUSH_STRAIGHT){
                return true;
            }else{
                if (highestCard.getSuit() > other.highestCard.getSuit()){
                    return true;
                }else if (highestCard.getSuit() == other.highestCard.getSuit()){
                    if (highestCard.getRank() > other.highestCard.getRank()){
                        return true;
                    }else{
                        return false;
                    }
                }
            }
        }
        
        //你出的牌型不符合规则
        System.out.println("你出的牌型不符合规则");
        return false;
    }
    
    /**
     * 获取牌型的字符串描述
     * 统一方法，既可内部使用也可外部调用
     */
    public int getType(){
        return patternType;
    }
    public static String getPatternTypeDisplayName(int type) {
        switch (type) {
            case INVALID: return "无效";
            case SINGLE: return "单牌";
            case PAIR: return "对子";
            case THREE_WITH_PAIR: return "三带二";
            case STRAIGHT: return "顺子";
            case FLUSH: return "同花";
            case FLUSH_STRAIGHT: return "同花顺";
            case BOMB: return "炸弹";
            default: return "系统错误，未知牌型";
        }
    }
} 