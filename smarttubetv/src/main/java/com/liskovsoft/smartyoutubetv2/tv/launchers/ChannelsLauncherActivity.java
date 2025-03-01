package com.liskovsoft.smartyoutubetv2.tv.launchers;

import android.content.Intent;
import android.os.Bundle;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;

public class ChannelsLauncherActivity extends MotherActivity implements SplashView {
    private static final String TAG = ChannelsLauncherActivity.class.getSimpleName();
    private Intent mNewIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mNewIntent = getIntent();

        BrowsePresenter.instance(this).selectSection(MediaGroup.TYPE_CHANNEL_UPLOADS);

        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        mNewIntent = intent;
    }

    @Override
    public Intent getNewIntent() {
        return mNewIntent;
    }
}
