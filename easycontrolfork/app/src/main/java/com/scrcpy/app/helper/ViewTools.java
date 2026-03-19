package com.scrcpy.app.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;

import java.util.Locale;

import com.scrcpy.app.R;
import com.scrcpy.app.databinding.ItemLoadingBinding;
import com.scrcpy.app.databinding.ItemSpinnerBinding;
import com.scrcpy.app.databinding.ItemSwitchBinding;
import com.scrcpy.app.databinding.ItemTextBinding;
import com.scrcpy.app.databinding.ModuleDialogBinding;
import com.scrcpy.app.entity.AppData;
import com.scrcpy.app.entity.MyInterface;

public class ViewTools {

  public static void setFullScreen(Activity context) {

    context.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    context.getWindow().getDecorView().setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_FULLSCREEN |
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  public static void setLocale(Activity context) {
    Resources resources = context.getResources();
    Configuration config = resources.getConfiguration();
    String locale = AppData.setting.getLocale();
    if (locale.equals("")) config.locale = Locale.getDefault();
    else if (locale.equals("en")) config.locale = Locale.ENGLISH;
    else if (locale.equals("zh")) config.locale = Locale.CHINESE;
    resources.updateConfiguration(config, resources.getDisplayMetrics());
  }

  public static void setStatusAndNavBar(Activity context) {

    context.getWindow().setNavigationBarColor(context.getResources().getColor(R.color.background));

    context.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    context.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    context.getWindow().setStatusBarColor(context.getResources().getColor(R.color.background));
    if ((context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES)
      context.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams lp = context.getWindow().getAttributes();
      lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      context.getWindow().setAttributes(lp);
    }
  }

  public static Dialog createDialog(Context context, boolean canCancel, View view) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setCancelable(canCancel);
    ScrollView dialogView = ModuleDialogBinding.inflate(LayoutInflater.from(context)).getRoot();
    dialogView.addView(view);
    builder.setView(dialogView);
    Dialog dialog = builder.create();
    dialog.setCanceledOnTouchOutside(canCancel);
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    return dialog;
  }

  public static Pair<ItemLoadingBinding, Dialog> createLoading(Context context) {
    ItemLoadingBinding loadingView = ItemLoadingBinding.inflate(LayoutInflater.from(context));
    return new Pair<>(loadingView, createDialog(context, false, loadingView.getRoot()));
  }

  public static ItemTextBinding createTextCard(
    Context context,
    String text,
    MyInterface.MyFunction function
  ) {
    ItemTextBinding textView = ItemTextBinding.inflate(LayoutInflater.from(context));
    textView.text.setText(text);
    if (function != null) textView.getRoot().setOnClickListener(v -> function.run());
    return textView;
  }

  public static ItemSwitchBinding createSwitchCard(
    Context context,
    String text,
    String textDetail,
    boolean config,
    MyInterface.MyFunctionBoolean function
  ) {
    ItemSwitchBinding switchView = ItemSwitchBinding.inflate(LayoutInflater.from(context));
    switchView.itemText.setText(text);
    switchView.itemDetail.setText(textDetail);
    switchView.itemSwitch.setChecked(config);
    if (function != null) switchView.itemSwitch.setOnCheckedChangeListener((buttonView, checked) -> function.run(checked));
    return switchView;
  }

  public static ItemSpinnerBinding createSpinnerCard(
    Context context,
    String text,
    String textDetail,
    String config,
    ArrayAdapter<String> adapter,
    MyInterface.MyFunctionString function
  ) {
    ItemSpinnerBinding spinnerView = ItemSpinnerBinding.inflate(LayoutInflater.from(context));
    spinnerView.itemText.setText(text);
    spinnerView.itemDetail.setText(textDetail);
    spinnerView.itemSpinner.setAdapter(adapter);
    spinnerView.itemSpinner.setSelection(adapter.getPosition(config));
    spinnerView.itemSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (function != null)
          function.run(spinnerView.itemSpinner.getSelectedItem().toString());
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
    return spinnerView;
  }

  public static void viewAnim(View view, boolean toShowView, int translationX, int translationY, MyInterface.MyFunctionBoolean action) {

    view.setTranslationX(toShowView ? translationX : 0);
    float endX = toShowView ? 0 : translationX;
    view.setTranslationY(toShowView ? translationY : 0);
    float endY = toShowView ? 0 : translationY;

    view.setAlpha(toShowView ? 0f : 1f);
    float endAlpha = toShowView ? 1f : 0f;

    ViewPropertyAnimator animator = view.animate()
      .translationX(endX)
      .translationY(endY)
      .alpha(endAlpha)
      .setDuration(toShowView ? 300 : 200)
      .setInterpolator(toShowView ? new OvershootInterpolator() : new DecelerateInterpolator());
    animator.withStartAction(() -> {
      if (action != null) action.run(true);
    });
    animator.withEndAction(() -> {
      if (action != null) action.run(false);
    });

    animator.start();
  }
}
