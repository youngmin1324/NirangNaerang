package org.androidtown.sharepic;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;


public class Photo implements Parcelable {

    public static final Creator<Photo> CREATOR = new Creator<Photo>() {
        @Override
        public Photo createFromParcel(Parcel source) {
            Uri thumbnailUri = Uri.CREATOR.createFromParcel(source);
            Uri imageUri = Uri.CREATOR.createFromParcel(source);
            return new Photo(thumbnailUri, imageUri);
        }

        @Override
        public Photo[] newArray(int size) {
            return new Photo[size];
        }
    };

    private Uri thumbnailUri;
    private Uri imageUri;

    public Photo(Uri thumbnail, Uri image) {
        thumbnailUri = (thumbnail == null)? image : thumbnail;
        imageUri = image;
    }

    public Uri getThumbnailUri() {
        return thumbnailUri;
    }

    public Uri getImageUri() {
        return imageUri;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        thumbnailUri.writeToParcel(dest, flags);
        imageUri.writeToParcel(dest, flags);

    }
}
