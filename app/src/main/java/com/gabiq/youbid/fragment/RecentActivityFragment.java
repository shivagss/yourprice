package com.gabiq.youbid.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.gabiq.youbid.R;
import com.gabiq.youbid.activity.DetailsActivity;
import com.gabiq.youbid.adapter.NotificationAdapter;
import com.gabiq.youbid.model.Notification;

import java.util.ArrayList;
import java.util.List;

public class RecentActivityFragment extends Fragment {
    ArrayList<Notification> notificationList;
    NotificationAdapter notificationAdapter;
    ListView lvNotifications;


    public static RecentActivityFragment newInstance() {
        RecentActivityFragment fragment = new RecentActivityFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }
    public RecentActivityFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_recent_activity, container, false);

        setupViews(view);
        return view;
    }

    private void setupViews(View view) {
        lvNotifications = (ListView) view.findViewById(R.id.lvNotifications);
        lvNotifications.setAdapter(notificationAdapter);

        lvNotifications.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Notification notification = notificationList.get(i);
                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                intent.putExtra("item_id", notification.getItemId());

                DetailsFragment.ViewType viewType = DetailsFragment.ViewType.Details;
                String type = notification.getType();
                if (type != null) {
                    if (type.equals("bid")) {
                        viewType = DetailsFragment.ViewType.Bids;
                    } else if (type.equals("comment")) {
                        viewType = DetailsFragment.ViewType.Comments;
                    } else if (type.equals("message")) {
                        viewType = DetailsFragment.ViewType.Bids;
                    }
                }
                intent.putExtra("viewType", viewType);

                startActivity(intent);
            }
        });

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        notificationList = new ArrayList<Notification>();
        notificationAdapter = new NotificationAdapter(getActivity(), notificationList);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotifications();
    }

    void loadNotifications() {
        notificationAdapter.clear();
        List<Notification> notificationList = Notification.getAll();

        notificationAdapter.addAll(notificationList);

        if (notificationAdapter.getCount() == 0) {
            // display empty message

        }
    }
}
