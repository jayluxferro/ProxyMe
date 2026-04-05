package tun.proxy;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.fragment.app.FragmentManager;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SettingsActivity extends AppCompatActivity {
    private static final String TITLE_TAG = "Settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.activity_settings, new SettingsFragment(), "preference_root")
                .commit();
        } else {
            setTitle(savedInstanceState.getCharSequence(TITLE_TAG));
        }
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setTitle(R.string.title_activity_settings);
            }
        });
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(TITLE_TAG, getTitle());
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }
        return super.onSupportNavigateUp();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener {
        public static final String VPN_CONNECTION_MODE = "vpn_connection_mode";
        public static final String VPN_DISALLOWED_APPLICATION_LIST = "vpn_disallowed_application_list";
        public static final String VPN_ALLOWED_APPLICATION_LIST = "vpn_allowed_application_list";
        public static final String VPN_CLEAR_ALL_SELECTION = "vpn_clear_all_selection";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preferences);
            setHasOptionsMenu(true);

            final ListPreference prefPackage = findPreference(VPN_CONNECTION_MODE);
            final PreferenceScreen prefDisallow = findPreference(VPN_DISALLOWED_APPLICATION_LIST);
            final PreferenceScreen prefAllow = findPreference(VPN_ALLOWED_APPLICATION_LIST);
            final PreferenceScreen clearAllSelection = findPreference(VPN_CLEAR_ALL_SELECTION);
            clearAllSelection.setOnPreferenceClickListener(this);

            prefPackage.setOnPreferenceChangeListener((preference, value) -> {
                if (preference instanceof ListPreference) {
                    final ListPreference listPreference = (ListPreference) preference;
                    int index = listPreference.findIndexOfValue((String) value);
                    prefDisallow.setEnabled(index == MyApplication.VPNMode.DISALLOW.ordinal());
                    prefAllow.setEnabled(index == MyApplication.VPNMode.ALLOW.ordinal());
                    preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

                    MyApplication.VPNMode mode = MyApplication.VPNMode.values()[index];
                    MyApplication.getInstance().storeVPNMode(mode);
                }
                return true;
            });
            prefPackage.setSummary(prefPackage.getEntry());
            prefDisallow.setEnabled(MyApplication.VPNMode.DISALLOW.name().equals(prefPackage.getValue()));
            prefAllow.setEnabled(MyApplication.VPNMode.ALLOW.name().equals(prefPackage.getValue()));

            updateMenuItem();
        }

        private void updateMenuItem() {
            final PreferenceScreen prefDisallow = findPreference(VPN_DISALLOWED_APPLICATION_LIST);
            final PreferenceScreen prefAllow = findPreference(VPN_ALLOWED_APPLICATION_LIST);

            int countDisallow = MyApplication.getInstance().loadVPNApplication(MyApplication.VPNMode.DISALLOW).size();
            int countAllow = MyApplication.getInstance().loadVPNApplication(MyApplication.VPNMode.ALLOW).size();
            prefDisallow.setTitle(getString(R.string.pref_header_disallowed_application_list) + String.format(" (%d)", countDisallow));
            prefAllow.setTitle(getString(R.string.pref_header_allowed_application_list) + String.format(" (%d)", countAllow));
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case VPN_DISALLOWED_APPLICATION_LIST:
                case VPN_ALLOWED_APPLICATION_LIST:
                    break;
                case VPN_CLEAR_ALL_SELECTION:
                    new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.title_activity_settings))
                        .setMessage(getString(R.string.pref_dialog_clear_all_application_msg))
                        .setPositiveButton("OK", (dialog, which) -> {
                            Set<String> set = new HashSet<>();
                            MyApplication.getInstance().storeVPNApplication(MyApplication.VPNMode.ALLOW, set);
                            MyApplication.getInstance().storeVPNApplication(MyApplication.VPNMode.DISALLOW, set);
                            updateMenuItem();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                    break;
            }
            return false;
        }
    }

    public static class DisallowedPackageListFragment extends PackageListFragment {
        public DisallowedPackageListFragment() {
            super(MyApplication.VPNMode.DISALLOW);
        }
    }

    public static class AllowedPackageListFragment extends PackageListFragment {
        public AllowedPackageListFragment() {
            super(MyApplication.VPNMode.ALLOW);
        }
    }

    protected static class PackageListFragment extends PreferenceFragmentCompat
            implements SearchView.OnQueryTextListener, SearchView.OnCloseListener {
        private final Map<String, Boolean> mAllPackageInfoMap = new HashMap<>();
        private static final String PREF_VPN_APPLICATION_ORDERBY = "pref_vpn_application_app_orderby";
        private static final String PREF_VPN_APPLICATION_FILTERBY = "pref_vpn_application_app_filterby";
        private static final String PREF_VPN_APPLICATION_SORTBY = "pref_vpn_application_app_sortby";

        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private Future<?> currentTask;

        private final MyApplication.VPNMode mode;
        private MyApplication.AppSortBy appSortBy = MyApplication.AppSortBy.APPNAME;
        private MyApplication.AppOrderBy appOrderBy = MyApplication.AppOrderBy.ASC;
        private MyApplication.AppSortBy appFilterBy = MyApplication.AppSortBy.APPNAME;
        private PreferenceScreen mFilterPreferenceScreen;

        String searchFilter = "";
        private SearchView searchView;

        public PackageListFragment(MyApplication.VPNMode mode) {
            super();
            this.mode = mode;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setHasOptionsMenu(true);
            mFilterPreferenceScreen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(mFilterPreferenceScreen);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);
            inflater.inflate(R.menu.menu_search, menu);

            final MenuItem menuSearch = menu.findItem(R.id.menu_search_item);
            this.searchView = (SearchView) menuSearch.getActionView();
            this.searchView.setOnQueryTextListener(this);
            this.searchView.setOnCloseListener(this);
            this.searchView.setSubmitButtonEnabled(false);

            switch (this.appOrderBy) {
                case ASC:
                    menu.findItem(R.id.menu_sort_order_asc).setChecked(true);
                    break;
                case DESC:
                    menu.findItem(R.id.menu_sort_order_desc).setChecked(true);
                    break;
            }

            switch (this.appFilterBy) {
                case APPNAME:
                    menu.findItem(R.id.menu_filter_app_name).setChecked(true);
                    break;
                case PKGNAME:
                    menu.findItem(R.id.menu_filter_pkg_name).setChecked(true);
                    break;
            }

            switch (this.appSortBy) {
                case APPNAME:
                    menu.findItem(R.id.menu_sort_app_name).setChecked(true);
                    break;
                case PKGNAME:
                    menu.findItem(R.id.menu_sort_pkg_name).setChecked(true);
                    break;
            }
        }

        protected void filter(String filter) {
            this.filter(filter, this.appFilterBy, this.appOrderBy, this.appSortBy);
        }

        protected void filter(String filter, final MyApplication.AppSortBy filterBy, final MyApplication.AppOrderBy orderBy, final MyApplication.AppSortBy sortBy) {
            if (filter == null) {
                filter = searchFilter;
            } else {
                searchFilter = filter;
            }
            this.appFilterBy = filterBy;
            this.appOrderBy = orderBy;
            this.appSortBy = sortBy;

            Set<String> selected = this.getAllSelectedPackageSet();
            storeSelectedPackageSet(selected);
            mFilterPreferenceScreen.removeAll();

            // Cancel any running task
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(true);
            }

            // Show progress
            mFilterPreferenceScreen.addPreference(new ProgressPreference(getActivity()));

            // Run package loading in background
            final String finalFilter = searchFilter;
            final MyApplication.AppSortBy finalFilterBy = appFilterBy;
            final MyApplication.AppOrderBy finalOrderBy = appOrderBy;
            final MyApplication.AppSortBy finalSortBy = appSortBy;

            currentTask = executor.submit(() -> {
                List<PackageInfo> packages = filterPackages(finalFilter, finalFilterBy, finalOrderBy, finalSortBy);
                if (Thread.currentThread().isInterrupted()) return;
                mainHandler.post(() -> onPackagesLoaded(packages, finalFilter, finalFilterBy));
            });
        }

        private List<PackageInfo> filterPackages(String filter, final MyApplication.AppSortBy filterBy, final MyApplication.AppOrderBy orderBy, final MyApplication.AppSortBy sortBy) {
            final Context context = MyApplication.getInstance().getApplicationContext();
            final PackageManager pm = context.getPackageManager();
            final List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
            Collections.sort(installedPackages, (o1, o2) -> {
                String t1 = "";
                String t2 = "";
                switch (sortBy) {
                    case APPNAME:
                        t1 = o1.applicationInfo.loadLabel(pm).toString();
                        t2 = o2.applicationInfo.loadLabel(pm).toString();
                        break;
                    case PKGNAME:
                        t1 = o1.packageName;
                        t2 = o2.packageName;
                        break;
                }
                if (MyApplication.AppOrderBy.ASC.equals(orderBy))
                    return t1.compareTo(t2);
                else
                    return t2.compareTo(t1);
            });
            final Map<String, Boolean> installedPackageMap = new HashMap<>();
            for (final PackageInfo pi : installedPackages) {
                if (Thread.currentThread().isInterrupted()) break;
                if (pi.packageName.equals(MyApplication.getInstance().getPackageName())) {
                    continue;
                }
                boolean checked = mAllPackageInfoMap.containsKey(pi.packageName) && mAllPackageInfoMap.get(pi.packageName);
                installedPackageMap.put(pi.packageName, checked);
            }
            mAllPackageInfoMap.clear();
            mAllPackageInfoMap.putAll(installedPackageMap);
            return installedPackages;
        }

        private void onPackagesLoaded(List<PackageInfo> installedPackages, String filter, MyApplication.AppSortBy filterBy) {
            final Context context = MyApplication.getInstance().getApplicationContext();
            final PackageManager pm = context.getPackageManager();
            mFilterPreferenceScreen.removeAll();
            for (final PackageInfo pi : installedPackages) {
                if (pi.packageName.equals(MyApplication.getInstance().getPackageName())) {
                    continue;
                }
                String t1 = "";
                String t2 = filter.trim();
                switch (filterBy) {
                    case APPNAME:
                        t1 = pi.applicationInfo.loadLabel(pm).toString();
                        break;
                    case PKGNAME:
                        t1 = pi.packageName;
                        break;
                }
                if (t2.isEmpty() || t1.toLowerCase().contains(t2.toLowerCase())) {
                    final Preference preference = buildPackagePreferences(pm, pi);
                    mFilterPreferenceScreen.addPreference(preference);
                }
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(true);
            }

            Set<String> selected = this.getAllSelectedPackageSet();
            storeSelectedPackageSet(selected);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance().getApplicationContext());
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(PREF_VPN_APPLICATION_ORDERBY, this.appOrderBy.name());
            edit.putString(PREF_VPN_APPLICATION_FILTERBY, this.appFilterBy.name());
            edit.putString(PREF_VPN_APPLICATION_SORTBY, this.appSortBy.name());
            edit.apply();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            executor.shutdownNow();
        }

        @Override
        public void onResume() {
            super.onResume();
            Set<String> loadMap = MyApplication.getInstance().loadVPNApplication(this.mode);
            for (String pkgName : loadMap) {
                this.mAllPackageInfoMap.put(pkgName, true);
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MyApplication.getInstance().getApplicationContext());
            String appOrderBy = prefs.getString(PREF_VPN_APPLICATION_ORDERBY, MyApplication.AppOrderBy.ASC.name());
            String appFilterBy = prefs.getString(PREF_VPN_APPLICATION_FILTERBY, MyApplication.AppSortBy.APPNAME.name());
            String appSortBy = prefs.getString(PREF_VPN_APPLICATION_SORTBY, MyApplication.AppSortBy.APPNAME.name());
            this.appOrderBy = Enum.valueOf(MyApplication.AppOrderBy.class, appOrderBy);
            this.appFilterBy = Enum.valueOf(MyApplication.AppSortBy.class, appFilterBy);
            this.appSortBy = Enum.valueOf(MyApplication.AppSortBy.class, appSortBy);
            filter(null);
        }

        private Preference buildPackagePreferences(final PackageManager pm, final PackageInfo pi) {
            final CheckBoxPreference prefCheck = new CheckBoxPreference(getActivity());
            prefCheck.setIcon(pi.applicationInfo.loadIcon(pm));
            prefCheck.setTitle(pi.applicationInfo.loadLabel(pm).toString());
            prefCheck.setSummary(pi.packageName);
            boolean checked = this.mAllPackageInfoMap.containsKey(pi.packageName) && this.mAllPackageInfoMap.get(pi.packageName);
            prefCheck.setChecked(checked);
            prefCheck.setOnPreferenceClickListener(preference -> {
                mAllPackageInfoMap.put(prefCheck.getSummary().toString(), prefCheck.isChecked());
                return false;
            });
            return prefCheck;
        }

        private Set<String> getFilterSelectedPackageSet() {
            final Set<String> selected = new HashSet<>();
            for (int i = 0; i < this.mFilterPreferenceScreen.getPreferenceCount(); i++) {
                Preference pref = this.mFilterPreferenceScreen.getPreference(i);
                if (pref instanceof CheckBoxPreference) {
                    CheckBoxPreference prefCheck = (CheckBoxPreference) pref;
                    if (prefCheck.isChecked()) {
                        selected.add(prefCheck.getSummary().toString());
                    }
                }
            }
            return selected;
        }

        private Set<String> getAllSelectedPackageSet() {
            final Set<String> selected = this.getFilterSelectedPackageSet();
            for (Map.Entry<String, Boolean> value : this.mAllPackageInfoMap.entrySet()) {
                if (value.getValue()) {
                    selected.add(value.getKey());
                }
            }
            return selected;
        }

        private void storeSelectedPackageSet(final Set<String> set) {
            MyApplication.getInstance().storeVPNMode(this.mode);
            MyApplication.getInstance().storeVPNApplication(this.mode, set);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            switch (id) {
                case android.R.id.home:
                    startActivity(new Intent(getActivity(), SettingsActivity.class));
                    return true;
                case R.id.menu_sort_order_asc:
                    item.setChecked(!item.isChecked());
                    filter(null, appFilterBy, MyApplication.AppOrderBy.ASC, appSortBy);
                    break;
                case R.id.menu_sort_order_desc:
                    item.setChecked(!item.isChecked());
                    filter(null, appFilterBy, MyApplication.AppOrderBy.DESC, appSortBy);
                    break;
                case R.id.menu_filter_app_name:
                    item.setChecked(!item.isChecked());
                    this.appFilterBy = MyApplication.AppSortBy.APPNAME;
                    break;
                case R.id.menu_filter_pkg_name:
                    item.setChecked(!item.isChecked());
                    this.appFilterBy = MyApplication.AppSortBy.PKGNAME;
                    break;
                case R.id.menu_sort_app_name:
                    item.setChecked(!item.isChecked());
                    filter(null, appFilterBy, appOrderBy, MyApplication.AppSortBy.APPNAME);
                    break;
                case R.id.menu_sort_pkg_name:
                    item.setChecked(!item.isChecked());
                    filter(null, appFilterBy, appOrderBy, MyApplication.AppSortBy.PKGNAME);
                    break;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            return searchFilter(query);
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            return searchFilter(newText);
        }

        private boolean searchFilter(String newText) {
            if (!newText.trim().isEmpty()) {
                filter(newText);
            } else {
                filter("");
                return false;
            }
            return true;
        }

        @Override
        public boolean onClose() {
            Set<String> selected = this.getAllSelectedPackageSet();
            storeSelectedPackageSet(selected);
            filter("");
            return false;
        }
    }

    protected static class ProgressPreference extends Preference {
        public ProgressPreference(Context context) {
            super(context);
            setLayoutResource(R.layout.preference_progress);
        }
    }
}
