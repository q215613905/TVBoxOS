package com.github.tvbox.osc.util;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class SiteSwitchDialogHelper {

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) {
            Toast.makeText(activity, "配置未加载，请先配置订阅地址", Toast.LENGTH_SHORT).show();
            return;
        }
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) {
            select = 0;
        }
        SelectDialog<SourceBean> dialog = new SelectDialog<>(activity);
        TvRecyclerView tvRecyclerView = dialog.findViewById(R.id.list);
        // 根据 sites 数量动态计算列数
        int spanCount = (int) Math.floor(sites.size() / 20.0);
        spanCount = Math.min(spanCount, 2);
        tvRecyclerView.setLayoutManager(new V7GridLayoutManager(activity, spanCount + 1));
        // 设置对话框宽度
        ConstraintLayout clRoot = dialog.findViewById(R.id.cl_root);
        ViewGroup.LayoutParams clp = clRoot.getLayoutParams();
        clp.width = AutoSizeUtils.mm2px(activity, 380 + 200 * spanCount);
        dialog.setTip("请选择首页数据源");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
            @Override
            public void click(SourceBean value, int pos) {
                dialog.dismiss();
                ApiConfig.get().setSourceBean(value);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HOME_SOURCE_CHANGE));
            }

            @Override
            public String getDisplay(SourceBean val) {
                return val.getName();
            }
        }, new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem == newItem;
            }

            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }
        }, sites, select);
        dialog.show();
    }
}