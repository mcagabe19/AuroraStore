/*
 * Aurora Store
 * Copyright (C) 2018  Rahul Kumar Patel <whyorean@gmail.com>
 *
 * Yalp Store
 * Copyright (C) 2018 Sergey Yeriomin <yeriomin@gmail.com>
 *
 * Aurora Store (a fork of Yalp Store )is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Aurora Store is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dragons.aurora.task.playstore;

import android.content.Context;

import com.dragons.aurora.ReviewStorageIterator;
import com.dragons.aurora.model.Review;

import java.util.ArrayList;
import java.util.List;

public class ReviewLoadTaskHelper extends ExceptionTask {

    private List<Review> mReviewList = new ArrayList<>();

    private ReviewStorageIterator iterator;

    public ReviewLoadTaskHelper(Context context) {
        super(context);
    }

    public void setIterator(ReviewStorageIterator iterator) {
        this.iterator = iterator;
    }

    public List<Review> getReviews() {
        mReviewList.clear();
        //Fetch 15 reviews at once :P
        mReviewList.addAll(iterator.next());
        mReviewList.addAll(iterator.next());
        mReviewList.addAll(iterator.next());
        mReviewList.addAll(iterator.next());
        mReviewList.addAll(iterator.next());
        return mReviewList;
    }
}