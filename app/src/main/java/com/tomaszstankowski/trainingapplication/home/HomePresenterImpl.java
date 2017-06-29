package com.tomaszstankowski.trainingapplication.home;


import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.StorageReference;
import com.tomaszstankowski.trainingapplication.Config;
import com.tomaszstankowski.trainingapplication.event.PhotoTransferEvent;
import com.tomaszstankowski.trainingapplication.event.TempImageFileTransferEvent;
import com.tomaszstankowski.trainingapplication.model.Photo;
import com.tomaszstankowski.trainingapplication.util.ImageManager;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HomePresenterImpl implements HomePresenter, HomeInteractor.OnLastPhotoChangeListener {

    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private ImageManager mManager;
    private HomeInteractor mInteractor;
    private Photo mPhoto;
    private File mTempImageFile;
    private HomeView mView;

    @Inject
    HomePresenterImpl(HomeInteractor interactor, ImageManager manager) {
        mInteractor = interactor;
        mManager = manager;
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mInteractor.stopObservingUserLastPhoto();
    }

    @Override
    public void onCreateView(HomeView view) {
        mView = view;
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            mInteractor.observeUserLastPhoto(firebaseUser.getUid(), this);
        }
    }

    @Override
    public void onLastPhotoChanged(Photo photo, StorageReference image) {
        mPhoto = photo;
        if (mView != null) {
            mView.updateView(image);
        }
    }

    @Override
    public void onLastPhotoRemoved() {
        mPhoto = null;
        if (mView != null) {
            mView.clearView();
        }
    }

    @Override
    public void onLastPhotoFetchError() {
        if (mView != null) {
            mView.showMessage(HomeView.Message.LOAD_ERROR);
        }
    }

    @Override
    public void onCaptureButtonClicked() {
        mTempImageFile = null;
        try {
            mTempImageFile = mManager.createImageFile();
            mView.startSystemCamera(mTempImageFile);
        } catch (Exception e) {
            mView.showMessage(HomeView.Message.CAMERA_ERROR);
        }
    }

    @Override
    public void onImageClicked() {
        if (mPhoto != null) {
            mView.startPhotoDetailsView();
            EventBus.getDefault().postSticky(
                    new PhotoTransferEvent(mPhoto, Config.RC_PHOTO_DETAILS)
            );
        }
    }

    @Override
    public void onSystemCameraResult(int resultCode) {
        if (resultCode == Config.CAMERA_RESULT_OK) {
            //We save Photo to database and then save image file in storage.
            //Listener might try to access image in storage before it's uploaded
            //so we stop observing database when save operation is performed
            mInteractor.stopObservingUserLastPhoto();
            mManager.addImageToSystemGallery(mTempImageFile);
            mView.startPhotoSaveView();
            EventBus.getDefault().postSticky(
                    new TempImageFileTransferEvent(mTempImageFile, Config.RC_PHOTO_SAVE)
            );
        } else {
            mView.showMessage(HomeView.Message.CAMERA_ERROR);
        }
    }

    @Override
    public void onPhotoSaveViewResult(int resultCode) {
        if (resultCode == Config.PHOTO_SAVE_OK) {
            FirebaseUser firebaseUser = mAuth.getCurrentUser();
            if (firebaseUser != null) {
                //was temporary removed just before starting PhotoSaveView
                mInteractor.observeUserLastPhoto(firebaseUser.getUid(), this);
            }
        }
    }
}
