package com.nekkochan.onyxchat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.nekkochan.onyxchat.MainActivity;
import com.nekkochan.onyxchat.R;

import java.util.ArrayList;
import java.util.List;

public class WelcomeActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button btnSkip, btnNext;
    private LinearLayout indicatorContainer;
    private List<OnboardingItem> onboardingItems;
    private OnboardingAdapter onboardingAdapter;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Check if app has been opened before
        if (sharedPreferences.getBoolean("first_time_completed", false)) {
            navigateToMainActivity();
            return;
        }

        viewPager = findViewById(R.id.viewPager);
        btnSkip = findViewById(R.id.btnSkip);
        btnNext = findViewById(R.id.btnNext);
        indicatorContainer = findViewById(R.id.indicatorContainer);

        setupOnboardingItems();
        setupOnboardingAdapter();
        setupIndicators();
        setCurrentIndicator(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentIndicator(position);
                
                if (position == onboardingItems.size() - 1) {
                    btnNext.setText("Get Started");
                } else {
                    btnNext.setText("Next");
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < onboardingItems.size() - 1) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void setupOnboardingItems() {
        onboardingItems = new ArrayList<>();
        
        OnboardingItem item1 = new OnboardingItem();
        item1.setTitle("Welcome to OnyxChat");
        item1.setDescription("A secure messaging app that protects your privacy");
        item1.setImageResource(R.drawable.ic_onboarding_welcome);
        
        OnboardingItem item2 = new OnboardingItem();
        item2.setTitle("End-to-End Encryption");
        item2.setDescription("Your messages are encrypted and can only be read by you and the recipient");
        item2.setImageResource(R.drawable.ic_onboarding_encryption);
        
        OnboardingItem item3 = new OnboardingItem();
        item3.setTitle("Privacy First");
        item3.setDescription("We prioritize your privacy with secure chats and data protection");
        item3.setImageResource(R.drawable.ic_onboarding_tor);

        onboardingItems.add(item1);
        onboardingItems.add(item2);
        onboardingItems.add(item3);
    }

    private void setupOnboardingAdapter() {
        onboardingAdapter = new OnboardingAdapter(onboardingItems);
        viewPager.setAdapter(onboardingAdapter);
    }

    private void setupIndicators() {
        ImageView[] indicators = new ImageView[onboardingItems.size()];
        
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(8, 0, 8, 0);
        
        for (int i = 0; i < indicators.length; i++) {
            indicators[i] = new ImageView(this);
            indicators[i].setImageResource(R.drawable.indicator_inactive);
            indicators[i].setLayoutParams(layoutParams);
            indicatorContainer.addView(indicators[i]);
        }
    }

    private void setCurrentIndicator(int position) {
        int childCount = indicatorContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) indicatorContainer.getChildAt(i);
            if (i == position) {
                imageView.setImageResource(R.drawable.indicator_active);
            } else {
                imageView.setImageResource(R.drawable.indicator_inactive);
            }
        }
    }

    private void finishOnboarding() {
        sharedPreferences.edit().putBoolean("first_time_completed", true).apply();
        navigateToMainActivity();
    }

    private void navigateToMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // Inner classes for ViewPager
    public static class OnboardingItem {
        private int imageResource;
        private String title;
        private String description;

        public int getImageResource() {
            return imageResource;
        }

        public void setImageResource(int imageResource) {
            this.imageResource = imageResource;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {
        
        private List<OnboardingItem> onboardingItems;
        
        public OnboardingAdapter(List<OnboardingItem> onboardingItems) {
            this.onboardingItems = onboardingItems;
        }
        
        @NonNull
        @Override
        public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new OnboardingViewHolder(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.item_onboarding, parent, false
                    )
            );
        }
        
        @Override
        public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
            holder.bind(onboardingItems.get(position));
        }
        
        @Override
        public int getItemCount() {
            return onboardingItems.size();
        }
        
        class OnboardingViewHolder extends RecyclerView.ViewHolder {
            
            private ImageView imageView;
            private TextView textTitle;
            private TextView textDescription;
            
            public OnboardingViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageOnboarding);
                textTitle = itemView.findViewById(R.id.textTitle);
                textDescription = itemView.findViewById(R.id.textDescription);
            }
            
            void bind(OnboardingItem onboardingItem) {
                imageView.setImageResource(onboardingItem.getImageResource());
                textTitle.setText(onboardingItem.getTitle());
                textDescription.setText(onboardingItem.getDescription());
            }
        }
    }
} 