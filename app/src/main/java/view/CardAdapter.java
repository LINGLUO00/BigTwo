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

            // 设置卡牌显示内容
            holder.tvCardValue.setText(card.getDisplayName());

            // 根据花色设置颜色
            int textColor = (card.getSuit() == Card.HEART || card.getSuit() == Card.DIAMOND) ? Color.RED : Color.BLACK;
            holder.tvCardValue.setTextColor(textColor);

            // 设置卡牌选中状态
            updateCardStyle(holder, card);

            // 设置点击监听器
            holder.cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCardClick(position);
                }
            });
        }
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
        TextView tvCardValue;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view);
            tvCardValue = itemView.findViewById(R.id.tv_card_value);
        }
    }
}
