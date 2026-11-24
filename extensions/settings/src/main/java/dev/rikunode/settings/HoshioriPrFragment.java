package dev.rikunode.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.hatena.android.bookmark.presentation.fragment.BaseFragment;

/** The host's pager requires a BaseFragment. Its real superclass comes from the target APK. */
public final class HoshioriPrFragment extends BaseFragment {
    public HoshioriPrFragment() {}

    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        return HoshioriPrTab.createView(inflater.getContext());
    }
}
