package com.github.tvbox.osc.ui.adapter;

import android.view.KeyEvent;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.DanmuSearchResult;

import java.util.ArrayList;

public class SearchDanmuAdapter extends BaseQuickAdapter<DanmuSearchResult, BaseViewHolder> {

    public SearchDanmuAdapter() {
        super(R.layout.item_search_danmu_result, new ArrayList<DanmuSearchResult>());
    }

    @Override
    protected void convert(BaseViewHolder helper, DanmuSearchResult item) {
        helper.setText(R.id.danmuName, item.getName());
        helper.addOnClickListener(R.id.danmuItem);
        helper.getView(R.id.danmuItem).setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                view.performClick();
                return true;
            }
            return false;
        });
    }
}
