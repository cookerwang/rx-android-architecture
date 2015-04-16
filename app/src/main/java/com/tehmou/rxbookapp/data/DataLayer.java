package com.tehmou.rxbookapp.data;

import com.tehmou.rxbookapp.network.NetworkApi;
import com.tehmou.rxbookapp.network.NetworkService;
import com.tehmou.rxbookapp.pojo.GitHubRepository;
import com.tehmou.rxbookapp.pojo.GitHubRepositorySearch;
import com.tehmou.rxbookapp.pojo.UserSettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.schedulers.Schedulers;


/**
 * Created by ttuo on 19/03/14.
 */
public class DataLayer {
    private static final String TAG = DataLayer.class.getSimpleName();
    private final NetworkApi networkApi;
    private final GitHubRepositoryStore gitHubRepositoryStore;
    private final GitHubRepositorySearchStore gitHubRepositorySearchStore;
    private final UserSettingsStore userSettingsStore;
    private final Context context;

    public DataLayer(ContentResolver contentResolver,
                     Context context) {
        this.context = context;
        networkApi = new NetworkApi();
        gitHubRepositoryStore = new GitHubRepositoryStore(contentResolver);
        gitHubRepositorySearchStore = new GitHubRepositorySearchStore(contentResolver);
        userSettingsStore = new UserSettingsStore(contentResolver);
    }

    public Observable<GitHubRepositorySearch> getGitHubRepositorySearch(final String search) {
        Observable.<List<GitHubRepository>>create((subscriber) -> {
                    try {
                        Map<String, String> params = new HashMap<>();
                        params.put("q", search);
                        List<GitHubRepository> results = networkApi.search(params);
                        subscriber.onNext(results);
                        subscriber.onCompleted();
                    } catch (Exception e) {
                        subscriber.onError(e);
                    }
                })
                .subscribeOn(Schedulers.computation())
                .map((repositories) -> {
                    final List<Integer> repositoryIds = new ArrayList<>();
                    for (GitHubRepository repository : repositories) {
                        gitHubRepositoryStore.put(repository);
                        repositoryIds.add(repository.getId());
                    }
                    return new GitHubRepositorySearch(search, repositoryIds);
                })
                .subscribe(gitHubRepositorySearchStore::put,
                        e -> Log.e(TAG, "Error fetching GitHub repository search for '" + search + "'", e));
        return gitHubRepositorySearchStore.getStream(search);
    }

    public Observable<GitHubRepository> getGitHubRepository(Integer repositoryId) {
        return gitHubRepositoryStore.getStream(repositoryId);
    }

    public Observable<GitHubRepository> fetchAndGetGitHubRepository(Integer repositoryId) {
        fetchGitHubRepository(repositoryId);
        return getGitHubRepository(repositoryId);
    }

    private void fetchGitHubRepository(Integer repositoryId) {
        Intent intent = new Intent(context, NetworkService.class);
        intent.putExtra("contentUriString", gitHubRepositoryStore.getContentUri().toString());
        intent.putExtra("id", repositoryId);
        context.startService(intent);
    }

    public Observable<UserSettings> getUserSettings() {
        return userSettingsStore.getStream(UserSettingsStore.DEFAULT_USER_ID);
    }

    public void setUserSettings(UserSettings userSettings) {
        userSettingsStore.insertOrUpdate(userSettings);
    }

    public static interface GetUserSettings {
        Observable<UserSettings> call();
    }

    public static interface SetUserSettings {
        void call(UserSettings userSettings);
    }

    public static interface GetGitHubRepository {
        Observable<GitHubRepository> call(int repositoryId);
    }

    public static interface FetchAndGetGitHubRepository extends GetGitHubRepository {

    }

    public static interface GetGitHubRepositorySearch {
        Observable<GitHubRepositorySearch> call(String search);
    }
}