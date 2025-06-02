package view;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.bigtwo.game.R;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import models.Card;
import util.AppExecutors;

/**
 * 卡牌适配器，用于显示玩家手牌
 */
public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

    private final List<Card> cards;
    private final OnCardClickListener listener;

    /**
     * 卡片点击监听器接口
     */
    public interface OnCardClickListener {
        void onCardClick(int position);
    }

    /**
     * 构造方法
     * @param cards 卡牌列表
     * @param listener 点击监听器
     */
    public CardAdapter(List<Card> cards, OnCardClickListener listener) {
        // 使用线程安全的列表
        this.cards = cards != null ? new CopyOnWriteArrayList<>(cards) : new CopyOnWriteArrayList<>();
        this.listener = listener;
    }

    /**
     * 更新卡牌列表
     * @param newCards 新的卡牌列表
     */
    public void updateCards(List<Card> newCards) {
        if (newCards == null) {
            this.cards.clear();
        } else {
            this.cards.clear();
            this.cards.addAll(newCards);
        }
        notifyDataSetChanged();  // 通知数据更新
    }

    /**
     * 清空卡片列表
     */
    public void clearCards() {
        this.cards.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        if (position >= 0 && position < cards.size()) {
            Card card = cards.get(position);

            // 获取花色和数值符号
            String suitSymbol = getSuitSymbol(card.getSuit());
            String rankSymbol = getRankSymbol(card.getRank());

            // 设置左上角内容
            holder.tvCardValue.setText(rankSymbol);
            holder.tvCardSuit.setText(suitSymbol);

            // 设置中间大花色
            holder.tvCardSuitLarge.setText(suitSymbol);

            // 设置右下角内容（旋转180度）
            holder.tvCardValueBottom.setText(rankSymbol);
            holder.tvCardSuitBottom.setText(suitSymbol);

            // 根据花色设置颜色（红/黑）
            int textColor = getSuitColor(card.getSuit());
            holder.tvCardValue.setTextColor(textColor);
            holder.tvCardSuit.setTextColor(textColor);
            holder.tvCardSuitLarge.setTextColor(textColor);
            holder.tvCardValueBottom.setTextColor(textColor);
            holder.tvCardSuitBottom.setTextColor(textColor);

            // 更新选中状态样式
            updateCardStyle(holder, card);

            // 点击事件
            holder.cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCardClick(position);
                }
            });
        }
    }

    // 获取花色符号（♦♣♥♠）
    private String getSuitSymbol(int suit) {
        switch (suit) {
            case Card.DIAMOND: return "♦";
            case Card.CLUB: return "♣";
            case Card.HEART: return "♥";
            case Card.SPADE: return "♠";
            default: return "";
        }
    }

    // 获取点数符号（3,4,...,A,2）
    private String getRankSymbol(int rank) {
        switch (rank) {
            case Card.THREE: return "3";
            case Card.FOUR: return "4";
            case Card.FIVE: return "5";
            case Card.SIX: return "6";
            case Card.SEVEN: return "7";
            case Card.EIGHT: return "8";
            case Card.NINE: return "9";
            case Card.TEN: return "10";
            case Card.JACK: return "J";
            case Card.QUEEN: return "Q";
            case Card.KING: return "K";
            case Card.ACE: return "A";
            case Card.TWO: return "2";
            default: return "";
        }
    }

    // 根据花色获取文字颜色（红色或黑色）
    private int getSuitColor(int suit) {
        return (suit == Card.HEART || suit == Card.DIAMOND) ? Color.RED : Color.BLACK;
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    /**
     * 更新卡牌样式，根据选中状态改变样式
     * @param holder CardViewHolder
     * @param card Card
     */
    private void updateCardStyle(CardViewHolder holder, Card card) {
        boolean isSelected = card.isSelected();
        if (isSelected) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#BBDEFB")); // 浅蓝色背景
            holder.cardView.setElevation(16f); // 增加阴影
            holder.cardView.setTranslationY(-20f); // 向上移动以表示选中
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.cardView.setElevation(4f);
            holder.cardView.setTranslationY(0f);
        }
    }

    /**
     * 卡牌ViewHolder
     */
    public static class CardViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvCardValue;       // 左上角数值
        TextView tvCardSuit;        // 左上角花色
        TextView tvCardSuitLarge;   // 中间大花色
        TextView tvCardValueBottom; // 右下角数值
        TextView tvCardSuitBottom;  // 右下角花色

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            tvCardValue = itemView.findViewById(R.id.tv_card_value);
            tvCardSuit = itemView.findViewById(R.id.tv_card_suit);
            tvCardSuitLarge = itemView.findViewById(R.id.tv_card_suit_large);
            tvCardValueBottom = itemView.findViewById(R.id.tv_card_value_bottom);
            tvCardSuitBottom = itemView.findViewById(R.id.tv_card_suit_bottom);
        }
    }
}
