package com.nekkochan.onyxchat.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel for the DocumentViewerActivity
 */
public class DocumentViewModel extends ViewModel {
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentPage = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> totalPages = new MutableLiveData<>(0);
    
    /**
     * Get the loading state
     * @return LiveData for the loading state
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    /**
     * Set the loading state
     * @param loading The loading state
     */
    public void setIsLoading(boolean loading) {
        isLoading.setValue(loading);
    }
    
    /**
     * Get the error message
     * @return LiveData for the error message
     */
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Set the error message
     * @param message The error message
     */
    public void setErrorMessage(String message) {
        errorMessage.setValue(message);
    }
    
    /**
     * Get the current page
     * @return LiveData for the current page
     */
    public LiveData<Integer> getCurrentPage() {
        return currentPage;
    }
    
    /**
     * Set the current page
     * @param page The current page
     */
    public void setCurrentPage(int page) {
        currentPage.setValue(page);
    }
    
    /**
     * Get the total pages
     * @return LiveData for the total pages
     */
    public LiveData<Integer> getTotalPages() {
        return totalPages;
    }
    
    /**
     * Set the total pages
     * @param pages The total pages
     */
    public void setTotalPages(int pages) {
        totalPages.setValue(pages);
    }
}
