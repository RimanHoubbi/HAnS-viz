package se.isselab.hansviz.JSONHandler;

import com.intellij.openapi.project.Project;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.cef.callback.CefQueryCallback;
import se.isselab.HAnS.featureHistoryView.FeatureCommitMapper;

import java.util.List;
import java.util.Map;

public class FeatureHistoryJSONHandler {
    private final CefQueryCallback callback;
    private final JSONType jsonType;
    private final Project project;

    public enum JSONType {FEATURE_HISTORY}

    public FeatureHistoryJSONHandler(Project project, CefQueryCallback callback, FeatureHistoryJSONHandler.JSONType type) {
        this.project = project;
        this.callback = callback;
        this.jsonType = type;
        handleFeatureHistoryRequest();
    }

    private void handleFeatureHistoryRequest() {
        // Call the mapFeaturesAndCommits method with a Runnable that handles the callback
        FeatureCommitMapper featureCommitMapper = new FeatureCommitMapper();
        featureCommitMapper.mapFeaturesAndCommits(project, () -> {
            // Once the data is ready, send the JSON response
            callback.success(getFeatureHistoryJSON(featureCommitMapper));
        });
    }

    /**
     * Method to generate JSON for Feature History.
     */
    private String getFeatureHistoryJSON(FeatureCommitMapper featureCommitMapper) {
        // Get the data
        JSONArray featuresArray = new JSONArray();
        JSONArray commitsArray = new JSONArray();
        JSONArray seriesDataArray = new JSONArray(); // New array for series data
        JSONArray deletedFeaturesArray = new JSONArray();

        // Add feature names
        for (String feature : featureCommitMapper.getFeatureNames()) {
            featuresArray.add(feature);
        }

        // Add commit times
        for (String commit : featureCommitMapper.getCommitTimes()) {
            commitsArray.add(commit);
        }

        // Add series data (list of coordinate pairs with commitHash)
        List<Map<String, Object>> seriesDataList = featureCommitMapper.getSeriesData();
        for (Map<String, Object> dataPoint : seriesDataList) {
            JSONObject point = new JSONObject();
            int featureIndex = (int) dataPoint.get("featureIndex");
            int commitIndex = (int) dataPoint.get("commitIndex");
            String commitHash = (String) dataPoint.get("commitHash");
            point.put("featureIndex", featureIndex);
            point.put("commitIndex", commitIndex);
            point.put("commitHash", commitHash);
            seriesDataArray.add(point);
        }

        for (String deletedFeature : featureCommitMapper.getDeletedFeatureNames()) {
            JSONObject deletedFeatureObject = new JSONObject();
            deletedFeatureObject.put("featureName", deletedFeature);
            deletedFeatureObject.put("lastCommitTime", featureCommitMapper.getDeletedFeatureCommits().get(deletedFeature));
            deletedFeatureObject.put("commitHash", featureCommitMapper.getDeletedFeatureCommitHashes().get(deletedFeature));
            deletedFeaturesArray.add(deletedFeatureObject);
        }

        // Create the JSON object
        JSONObject json = new JSONObject();
        json.put("type", "featureHistory");
        json.put("features", featuresArray);
        json.put("commits", commitsArray);
        json.put("seriesData", seriesDataArray);
        json.put("deletedFeatures", deletedFeaturesArray);

        // Debug output
        System.out.println("Feature History JSON: " + json);

        return json.toJSONString();
    }
}

