package org.cyphy_lab.lapd.core;

import android.util.Pair;

import com.google.ar.core.Pose;

import org.cyphy_lab.lapd.arcore.RelativeAnchor;
import org.cyphy_lab.lapd.config.PhoneLidarConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsManager {

    private static final String LINE_BREAKER = "---------------------------------------\n";

    private static final List<Integer> list_of_num_blobs_after_2d_filters = new ArrayList<>();
    private static final List<Integer> list_of_num_anchors_created = new ArrayList<>();
    private static final List<Integer> list_of_num_anchors_added_to_existing = new ArrayList<>();
    private static final List<Integer> list_of_total_num_anchors = new ArrayList<>();
    private static final List<Integer> list_of_num_anchors_removed = new ArrayList<>();
    private static final float[] heatmap_metrics = new float[9];
    private static final String heatmap_coords = "";

    // Constructor does nothing
    public MetricsManager() {
    }

    public void clearMetrics() {
        list_of_num_blobs_after_2d_filters.clear();
        list_of_total_num_anchors.clear();
        list_of_num_anchors_created.clear();
        list_of_num_anchors_added_to_existing.clear();
        list_of_num_anchors_removed.clear();
    }

    public void recordAnchorsPerFrame(int num_anchors_created, int num_anchors_added_to_existing,
                                      int total_num_of_anchors_before) {
        list_of_num_anchors_created.add(num_anchors_created);
        list_of_num_anchors_added_to_existing.add(num_anchors_added_to_existing);
        list_of_total_num_anchors.add(total_num_of_anchors_before);
    }

    public void record2dBlobsFiltered(int num_blobs) {
        list_of_num_blobs_after_2d_filters.add(num_blobs);
    }

    public void recordAnchorsRemoved(int num_anchors_removed) {
        list_of_num_anchors_removed.add(num_anchors_removed);
    }

    public void recordHeatmap(HashMap<RelativeAnchor, Pair<Float, List<Pose>>> anchorHeatmap) {
        // get total number of anchors
        int total_num_anchors = anchorHeatmap.keySet().size();
        int num_suspicious_anchors = 0;
        int num_confirmed_anchors = 0;
        int num_dead_anchors = 0;
        float sum_suspicious_anchor_score = 0;
        float sum_confirmed_anchor_score = 0;
        int sum_suspicious_poses = 0;
        int sum_confirmed_poses = 0;
        int sum_dead_anchor_poses = 0;

        // loop through the anchor heatmap to check the anchor status
        for (Map.Entry<RelativeAnchor, Pair<Float, List<Pose>>> entry : anchorHeatmap.entrySet()) {
            RelativeAnchor anchor = entry.getKey();
            Pair<Float, List<Pose>> value = entry.getValue();
            float anchorScore = value.first;
            List<Pose> anchorPoses = value.second;

            if (anchorScore > PhoneLidarConfig.FOV_INFORMATION_THRESHOLD_SCORE) {
                // check if anchor is confirmed (anchors colored green)
                num_confirmed_anchors++;
                sum_confirmed_anchor_score += anchorScore;
                sum_confirmed_poses += anchorPoses.size();
            } else if (anchorScore > PhoneLidarConfig.ANCHOR_MIN_SCORE) {
                // check if anchor is suspicious (anchors colored orange)
                num_suspicious_anchors++;
                sum_suspicious_anchor_score += anchorScore;
                sum_suspicious_poses += anchorPoses.size();
            }

            // check if anchor is dead
            if (anchor.isDead) {
                num_dead_anchors++;
                sum_dead_anchor_poses += anchorPoses.size();
            }
        }

        float average_suspicious_anchor_score;
        float average_suspicious_anchor_poses;
        if (num_suspicious_anchors > 0) {
            average_suspicious_anchor_score = sum_suspicious_anchor_score / (float) num_suspicious_anchors;
            average_suspicious_anchor_poses = (float) sum_suspicious_poses / (float) num_suspicious_anchors;
        } else {
            average_suspicious_anchor_score = 0;
            average_suspicious_anchor_poses = 0;
        }

        float average_confirmed_anchor_score;
        float average_confirmed_anchor_poses;
        if (num_confirmed_anchors > 0) {
            average_confirmed_anchor_score = sum_confirmed_anchor_score / (float) num_confirmed_anchors;
            average_confirmed_anchor_poses = (float) sum_confirmed_poses / (float) num_confirmed_anchors;
        } else {
            average_confirmed_anchor_score = 0;
            average_confirmed_anchor_poses = 0;
        }

        float average_dead_anchor_poses;
        if (num_dead_anchors > 0) {
            average_dead_anchor_poses = (float) sum_dead_anchor_poses / (float) num_dead_anchors;
        } else {
            average_dead_anchor_poses = 0;
        }

        // insert to internal data structure
        heatmap_metrics[0] = total_num_anchors;
        heatmap_metrics[1] = num_suspicious_anchors;
        heatmap_metrics[2] = average_suspicious_anchor_score;
        heatmap_metrics[3] = average_suspicious_anchor_poses;
        heatmap_metrics[4] = num_confirmed_anchors;
        heatmap_metrics[5] = average_confirmed_anchor_score;
        heatmap_metrics[6] = average_confirmed_anchor_poses;
        heatmap_metrics[7] = num_dead_anchors;
        heatmap_metrics[8] = average_dead_anchor_poses;
//
//        StringBuilder allPoints = new StringBuilder();
//
//        for (PhoneLidarMainActivity.com.sensg.phonelidar.arcore.RelativeAnchor r : anchorHeatmap.keySet()) {
//           allPoints.append(r.initialPos[0] + "\t" + r.initialPos[1] + "\t" + r.initialPos[2] + "\t" + r.lockedOn);
//           allPoints.append('\n');
//        }
//        heatmap_coords = allPoints.toString();
    }

    private String formatList(List<Integer> list) {
        if (list.size() == 0) return "None\n";
        StringBuilder sb = new StringBuilder();
        for (int ele : list) {
            sb.append(ele);
            sb.append(',');
        }
        sb.append('\n');
        return sb.toString();
    }

    private float averageList(List<Integer> list) {
        if (list.size() == 0) return 0;
        int sum = 0;
        for (int ele : list) {
            sum += ele;
        }
        return (float) sum / (float) list.size();
    }

    public String formatMetrics() {

        //
//        sb.append("Individual points \n");
//        sb.append(heatmap_coords);
//        sb.append(LINE_BREAKER);

        return "Metrics\n" +
                LINE_BREAKER +

                // Report metrics per frame
                "Per Frame Metrics\n" +
                "Num of 2D blobs filtered: " +
                formatList(list_of_num_blobs_after_2d_filters) +
                "Total num of 3D anchors before creation of new anchors: " +
                formatList(list_of_total_num_anchors) +
                "Num of 3D anchors created: " +
                formatList(list_of_num_anchors_created) +
                "Num of 3D anchors added to existing: " +
                formatList(list_of_num_anchors_added_to_existing) +
                LINE_BREAKER +

                // Report num of anchors removed
                "Num of anchors removed: " +
                formatList(list_of_num_anchors_removed) +
                LINE_BREAKER +

                // Report average metrics
                "Average Metrics\n" +
                "Average num of 2D blobs filtered: " +
                averageList(list_of_num_blobs_after_2d_filters) +
                '\n' +
                "Average num of 3D anchors created: " +
                averageList(list_of_num_anchors_created) +
                '\n' +
                "Average num of 3D anchors added to existing: " +
                averageList(list_of_num_anchors_added_to_existing) +
                '\n' +
                LINE_BREAKER +

                // Report heat map metrics
                "Heatmap Metrics\n" +
                "Total num of anchors in the heatmap: " +
                heatmap_metrics[0] +
                '\n' +
                "Num of suspicious anchors (orange): " +
                heatmap_metrics[1] +
                '\n' +
                "Average score of suspicious anchors: " +
                heatmap_metrics[2] +
                '\n' +
                "Average num of poses of suspicious anchors: " +
                heatmap_metrics[3] +
                '\n' +
                "Num of confirmed anchors (green): " +
                heatmap_metrics[4] +
                '\n' +
                "Average score of confirmed anchors: " +
                heatmap_metrics[5] +
                '\n' +
                "Average num of poses of confirmed anchors: " +
                heatmap_metrics[6] +
                '\n' +
                "Num of dead anchors (grey): " +
                heatmap_metrics[7] +
                '\n' +
                "Average num of poses of dead anchors: " +
                heatmap_metrics[8] +
                '\n' +
                LINE_BREAKER;
    }
}
