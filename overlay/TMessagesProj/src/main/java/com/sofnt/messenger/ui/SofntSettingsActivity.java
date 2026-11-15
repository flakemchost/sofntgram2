package com.sofnt.messenger.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sofnt.messenger.SofntConfig;
import com.sofnt.messenger.SofntKeys;
import com.sofnt.messenger.profile.localdata.ProfileData;
import com.sofnt.messenger.profile.localdata.ProfileDataStore;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/**
 * SoFntGram settings screen. Written for SoFntGram on top of Telegram's own base classes.
 * Every row here changes something that already works; new sections are added together with their features.
 */
public class SofntSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_VALUE = 2;
    private static final int TYPE_SHADOW = 3;
    private static final int TYPE_INFO = 4;

    private RecyclerListView listView;
    private ListAdapter adapter;

    private int rowCount;
    private int profileHeaderRow;
    private int phoneRow;
    private int usernamesRow;
    private int streamerRow;
    private int streamerInfoRow;
    private int aboutHeaderRow;
    private int versionRow;
    private int aboutInfoRow;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.sofntProfileDataChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.sofntConfigChanged);
        updateRows();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.sofntProfileDataChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.sofntConfigChanged);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateRows() {
        rowCount = 0;
        profileHeaderRow = rowCount++;
        phoneRow = rowCount++;
        usernamesRow = rowCount++;
        streamerRow = rowCount++;
        streamerInfoRow = rowCount++;
        aboutHeaderRow = rowCount++;
        versionRow = rowCount++;
        aboutInfoRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.SofntSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        adapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == phoneRow) {
                SofntEditors.editPhone(this);
            } else if (position == usernamesRow) {
                SofntEditors.editUsernames(this);
            } else if (position == streamerRow) {
                boolean enabled = !SofntConfig.isStreamerMode();
                SofntConfig.put(SofntKeys.STREAMER_MODE, enabled);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(enabled);
                }
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private long ownId() {
        return UserConfig.getInstance(currentAccount).getClientUserId();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == phoneRow || position == usernamesRow || position == streamerRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == profileHeaderRow || position == aboutHeaderRow) {
                return TYPE_HEADER;
            } else if (position == streamerRow) {
                return TYPE_CHECK;
            } else if (position == phoneRow || position == usernamesRow || position == versionRow) {
                return TYPE_VALUE;
            } else if (position == streamerInfoRow || position == aboutInfoRow) {
                return TYPE_INFO;
            }
            return TYPE_SHADOW;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_VALUE:
                    view = new TextCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(context);
                    break;
                default:
                    view = new ShadowSectionCell(context);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(position == profileHeaderRow
                            ? LocaleController.getString(R.string.SofntSettingsProfile)
                            : LocaleController.getString(R.string.SofntSettingsAbout));
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck(LocaleController.getString(R.string.SofntStreamerMode), SofntConfig.isStreamerMode(), false);
                    break;
                }
                case TYPE_VALUE: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == phoneRow) {
                        ProfileData data = ProfileDataStore.getOwn(ownId());
                        String shown = data.phone != null && !data.phone.isEmpty() ? "+" + data.phone : LocaleController.getString(R.string.SofntPhoneAsTelegram);
                        cell.setTextAndValue(LocaleController.getString(R.string.SofntEditPhone), shown, false);
                    } else if (position == usernamesRow) {
                        ProfileData data = ProfileDataStore.getOwn(ownId());
                        StringBuilder names = new StringBuilder();
                        for (String name : data.secondUsernames) {
                            if (names.length() > 0) {
                                names.append(", ");
                            }
                            names.append('@').append(name);
                        }
                        String shown = names.length() > 0 ? names.toString() : LocaleController.getString(R.string.SofntNoneSet);
                        cell.setTextAndValue(LocaleController.getString(R.string.SofntEditUsernames), shown, false);
                    } else {
                        cell.setTextAndValue(LocaleController.getString(R.string.SofntVersion), "SoFntGram, Telegram " + BuildVars.BUILD_VERSION_STRING, false);
                    }
                    break;
                }
                case TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackgroundDrawable(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    cell.setText(position == streamerInfoRow
                            ? LocaleController.getString(R.string.SofntStreamerModeInfo)
                            : LocaleController.getString(R.string.SofntAboutInfo));
                    break;
                }
                default:
                    break;
            }
        }
    }
}
