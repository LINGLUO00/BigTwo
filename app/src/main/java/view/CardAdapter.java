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

import java.util.ArrayList;
import java.util.List;

import models.Card;

/**
 * 卡牌适配器，用于显示玩家手牌
 */
public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {
    
    private List<Card> cards;
    private OnCardClickListener listener;
    
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
        // 安全处理null值
        this.cards = cards != null ? cards : new ArrayList<>();
        this.listener = listener;
        System.out.println("CardAdapter初始化，卡牌数量: " + this.cards.size());
    }
    
    /**
     * 更新卡牌列表
     * @param newCards 新的卡牌列表
     */
    public void updateCards(List<Card> newCards) {
        // 安全处理null值
        if (newCards == null) {
            System.out.println("警告：尝试更新为null的卡牌列表");
            this.cards.clear();
        } else {
            this.cards = newCards;
            System.out.println("更新卡牌列表，数量: " + newCards.size());
        }
        notifyDataSetChanged();
    }
    
    /**
     * 清空卡片列表
     */
    public void clearCards() {
        if (cards != null) {
            cards.clear();
        }
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
            int textColor;
            switch (card.getSuit()) {
                case Card.HEART:
                case Card.DIAMOND:
                    textColor = Color.RED;
                    break;
                default:
                    textColor = Color.BLACK;
            }
            holder.tvCardValue.setTextColor(textColor);
            
            // 设置卡牌选中状态
            boolean isSelected = card.isSelected();
            System.out.println("渲染卡牌: " + card.getDisplayName() + ", 选中状态: " + isSelected);
            
            if (isSelected) {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#BBDEFB")); // 浅蓝色背景
                holder.cardView.setElevation(16f); // 增加阴影
                holder.cardView.setTranslationY(-20f); // 向上移动以表示选中
            } else {
                holder.cardView.setCardBackgroundColor(Color.WHITE);
                holder.cardView.setElevation(4f);
                holder.cardView.setTranslationY(0f);
            }
            
            // 设置点击监听器
            final int pos = position; // 创建最终变量用于回调
            holder.cardView.setOnClickListener(v -> {
                System.out.println("点击卡牌 " + pos + ": " + card.getDisplayName());
                if (listener != null) {
                    listener.onCardClick(pos);
                }
            });
        } else {
            System.out.println("警告: 请求渲染越界的卡牌位置: " + position);
        }
    }
    
    @Override
    public int getItemCount() {
        // 安全处理null值
        return cards == null ? 0 : cards.size();
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