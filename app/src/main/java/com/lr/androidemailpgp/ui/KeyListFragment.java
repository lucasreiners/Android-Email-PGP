package com.lr.androidemailpgp.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.lr.androidemailpgp.R;

import java.util.ArrayList;

/**
 * A fragment representing a list of Items.
 * <p />
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p />
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class KeyListFragment extends Fragment implements AdapterView.OnItemClickListener {

    private static final String ARG_KEYS = "arg_keys";
    private static final String ARG_TYPE = "arg_type";

    private ArrayList<String> keysList;
    private String keyType;



    /**
     * The fragment's ListView/GridView.
     */
    private AbsListView mListView;

    /**
     * The Adapter which will be used to populate the ListView/GridView with
     * Views.
     */
    private ListAdapter mAdapter;

    public static KeyListFragment newInstance(ArrayList<String> strings, String keyType) {
        KeyListFragment fragment = new KeyListFragment();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_KEYS, strings);
        args.putString(ARG_TYPE, keyType);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public KeyListFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            keysList = getArguments().getStringArrayList(ARG_KEYS);
            keyType = getArguments().getString(ARG_TYPE);
        }

        mAdapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_1, android.R.id.text1, keysList);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_keylist, container, false);

        // Set the adapter
        mListView = (AbsListView) view.findViewById(android.R.id.list);
        ((AdapterView<ListAdapter>) mListView).setAdapter(mAdapter);

        mListView.setOnItemClickListener(this);

        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String email = keysList.get(position);
        String key = getActivity().getSharedPreferences("keys", Context.MODE_WORLD_READABLE).getString("key_" + keyType + "_" + email, "");

        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
        dialog.setTitle(email);
        dialog.setMessage(key);
        dialog.setPositiveButton(android.R.string.ok, null);
        dialog.show();
    }

    /**
     * The default content for this Fragment has a TextView that is shown when
     * the list is empty. If you would like to change the text, call this method
     * to supply the text it should use.
     */
    public void setEmptyText(CharSequence emptyText) {
        View emptyView = mListView.getEmptyView();

        if (emptyText instanceof TextView) {
            ((TextView) emptyView).setText(emptyText);
        }
    }


}
