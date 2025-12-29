package org.dromara.camera.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoAnalysisResultDTO {

    private String fileName;
    private String fileSize;
    private String analysisTime;
    private boolean success;
    private String message;
    private UploadInfoDTO uploadInfo;
    private AnalysisResultDTO analysisResult;
    private String analysisError;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadInfoDTO {
        private String remotePath;
        private String filename;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResultDTO {
        private boolean success;
        private List<EventDTO> events;
        private Integer totalEvents;
        private String analysisMethod;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDTO {
        private String eventDescription;
        private String date;
        private String startTime;
        private String endTime;
        private String userNumber;
        private String unitNumber;
        private String serialNumber;
    }
}
