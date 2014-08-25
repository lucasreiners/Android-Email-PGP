package com.lr.androidemailpgp.ui;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.lr.androidemailpgp.R;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

public class KeyManagementActivity extends ActionBarActivity implements ActionBar.TabListener {


    //Variables
    private ArrayList<String> privateKeysEmailAdresses;
    private ArrayList<String> publicKeysEmailAdresses;


    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_management);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        Intent openIntent = getIntent();
        if (openIntent.getAction().equals(Intent.ACTION_VIEW)){
            handleFileInput(openIntent);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        privateKeysEmailAdresses = new ArrayList<String>();
        publicKeysEmailAdresses = new ArrayList<String>();

        SharedPreferences prefs = getSharedPreferences("keys", MODE_WORLD_READABLE);
        Map<String, ?> keysSet = prefs.getAll();
        for(Map.Entry<String, ?> key : keysSet.entrySet()){
            if(key.getKey().contains("_private_")){
                privateKeysEmailAdresses.add(key.getKey().replace("key_private_", ""));
            } else if (key.getKey().contains("_public_")){
                publicKeysEmailAdresses.add(key.getKey().replace("key_public_", ""));
            }
        }
    }

    private void handleFileInput(Intent openIntent){
        Uri fileUri = openIntent.getData();
        ContentResolver cr = getContentResolver();
        try {
            InputStream keyInputStream = cr.openInputStream(fileUri);
            String keyContent = convertStreamToString(keyInputStream);
            keyInputStream.close();

            String privKey = null;
            String publKey = null;

            if(keyContent.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----") && keyContent.contains("-----END PGP PUBLIC KEY BLOCK-----")){
                publKey = keyContent.substring(keyContent.indexOf("-----BEGIN PGP PUBLIC KEY BLOCK-----"),
                        keyContent.indexOf("-----END PGP PUBLIC KEY BLOCK-----") + ("-----END PGP PUBLIC KEY BLOCK-----").length());
            }

            if(keyContent.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----") && keyContent.contains("-----END PGP PRIVATE KEY BLOCK-----")){
                privKey = keyContent.substring(keyContent.indexOf("-----BEGIN PGP PRIVATE KEY BLOCK-----"),
                        keyContent.indexOf("-----END PGP PRIVATE KEY BLOCK-----") + ("-----END PGP PRIVATE KEY BLOCK-----").length());
            }

            LinearLayout dialogView = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_keyimport, null);
            final EditText emailInput = (EditText) dialogView.findViewById(R.id.dialog_keyimport_email);
            CheckBox importPrivateKeyInput = (CheckBox) dialogView.findViewById(R.id.dialog_keyimport_checkbox_privatekey);
            final CheckBox importPublicKeyInput = (CheckBox) dialogView.findViewById(R.id.dialog_keyimport_checkbox_publickey);

            if(privKey == null){
                importPrivateKeyInput.setEnabled(false);
                importPrivateKeyInput.setPaintFlags(importPrivateKeyInput.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                importPrivateKeyInput.setChecked(true);
            }

            if(publKey == null){
                importPublicKeyInput.setEnabled(false);
                importPublicKeyInput.setPaintFlags(importPublicKeyInput.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                importPublicKeyInput.setChecked(true);
            }

            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("Key import");
            dialog.setView(dialogView);
            dialog.setCancelable(false);
            final String finalPublKey = publKey;
            final String finalPrivKey = privKey;
            dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(!emailInput.getText().toString().isEmpty()){
                        if(finalPublKey != null && importPublicKeyInput.isChecked()){
                            saveKey("public", emailInput.getText().toString(), finalPublKey);
                        }
                        if(finalPrivKey != null && importPublicKeyInput.isChecked()){
                            saveKey("private", emailInput.getText().toString(), finalPrivKey);
                        }
                        Toast.makeText(getApplicationContext(), getString(R.string.message_keys_saved), Toast.LENGTH_LONG).show();
                    }
                }
            });
            dialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            dialog.show();


        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "IO Exception", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveKey(String type, String email, String key){
        SharedPreferences.Editor edit = getSharedPreferences("keys", MODE_WORLD_READABLE).edit();
        edit.putString("key_" + type + "_" + email, key);
        edit.apply();
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.key_management, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return KeyListFragment.newInstance(privateKeysEmailAdresses, "private");
                case 1:
                    return KeyListFragment.newInstance(publicKeysEmailAdresses, "public");
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.private_keys);
                case 1:
                    return getString(R.string.public_keys);
            }
            return null;
        }
    }
}
