package com.tbm.careerpathlearning.service;

/** A file made for download: the HRDC attendance template or a session report, as an Excel workbook. */
public record TrainingDownload(byte[] content, String fileName, String contentType) {
}
