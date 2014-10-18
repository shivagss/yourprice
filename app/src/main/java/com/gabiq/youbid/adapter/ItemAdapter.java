package com.gabiq.youbid.adapter;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.gabiq.youbid.R;
import com.gabiq.youbid.model.Favorite;
import com.gabiq.youbid.model.Item;
import com.parse.GetCallback;
import com.parse.GetDataCallback;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseImageView;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;
import com.parse.ParseUser;

import java.util.Random;

public class ItemAdapter extends ParseQueryAdapter<Item> {

    public ItemAdapter(Context context, ParseQueryAdapter.QueryFactory<Item> parseQuery) {
        super(context, parseQuery);
    }


    @Override
    public View getItemView(final Item item, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;

        if (convertView == null) {
            convertView = View.inflate(getContext(), R.layout.layout_item_cell, null);

            viewHolder = new ViewHolder();
            viewHolder.tvItemCellCaption = (TextView) convertView.findViewById(R.id.tvItemCellCaption);
            viewHolder.ivItemCellImage = (ParseImageView) convertView.findViewById(R.id.ivItemCellImage);
            viewHolder.btnItemCellFavorite = (Button) convertView.findViewById(R.id.btnItemCellFavorite);

            viewHolder.btnItemCellFavorite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d("INFO", "clicked");
                    boolean statePressed = false;
                    int[] states = viewHolder.btnItemCellFavorite.getDrawableState();
                    for (int state : states) {
                        if (state == android.R.attr.state_pressed) {
                            statePressed = true;
                        }
                    }

                    if (statePressed) {
                        Log.d("INFO", "*********************** unselect button");
                    } else {
                        Favorite.setFavorite(item, true);
                    }

                }
            });

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        super.getItemView(item, convertView, parent);

        Random r = new Random();
        switch (r.nextInt(5)) {
            case 0:
                viewHolder.ivItemCellImage.setPlaceholder(getContext().getResources().getDrawable(R.color.placeholder1));
                break;
            case 1:
                viewHolder.ivItemCellImage.setPlaceholder(getContext().getResources().getDrawable(R.color.placeholder2));
                break;
            case 2:
                viewHolder.ivItemCellImage.setPlaceholder(getContext().getResources().getDrawable(R.color.placeholder3));
                break;
            case 3:
                viewHolder.ivItemCellImage.setPlaceholder(getContext().getResources().getDrawable(R.color.placeholder4));
                break;
            case 4:
                viewHolder.ivItemCellImage.setPlaceholder(getContext().getResources().getDrawable(R.color.placeholder5));
                break;
        }

        int i = 6; // r.nextInt(10);

        viewHolder.tvItemCellCaption.setText(item.getString("caption"));
        ParseFile photoFile = item.getParseFile("thumbnail");
        if (photoFile != null) {
            viewHolder.ivItemCellImage.setParseFile(photoFile);
//            imageView.setMinimumHeight(200+i*25);
//            imageView.setMaxHeight(200+i*25);
            viewHolder.ivItemCellImage.loadInBackground(new GetDataCallback() {
                @Override
                public void done(byte[] data, ParseException e) {
                    // nothing to do
                }
            });
        } else {
            viewHolder.ivItemCellImage.setParseFile(null);
        }

        // set favorite
        viewHolder.btnItemCellFavorite.setVisibility(View.INVISIBLE);
        ParseQuery<Favorite> query = ParseQuery.getQuery("Favorite");
        query.whereEqualTo("user", ParseUser.getCurrentUser());
        query.whereEqualTo("itemId", item.getObjectId());
        final ViewHolder vh = viewHolder;
        query.getFirstInBackground(new GetCallback<Favorite>() {
            public void done(Favorite favorite, ParseException e) {
                vh.btnItemCellFavorite.setVisibility(View.VISIBLE);
                if (favorite == null) {
                    vh.btnItemCellFavorite.setPressed(false);
                    Log.d("INFO", "%%%%%%%%%%%%%%%%%%%%%% not selected");
                } else {
                    vh.btnItemCellFavorite.setPressed(true);
                    Log.d("INFO", "***************************** selected");
                }
            }
        });

        return convertView;
    }

    private static class ViewHolder {
        TextView tvItemCellCaption;
        ParseImageView ivItemCellImage;
        Button btnItemCellFavorite;
    }

}
