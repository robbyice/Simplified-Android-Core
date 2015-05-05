package org.nypl.simplified.downloader.core;

import java.io.File;

public interface DownloadListenerType
{
  void downloadCancelled(
    DownloadSnapshot snap);

  void downloadCleanedUp(
    DownloadSnapshot snap);

  void downloadCompleted(
    DownloadSnapshot snap);

  void downloadCompletedTake(
    DownloadSnapshot snap,
    File file_data);

  void downloadCompletedTakeFailed(
    DownloadSnapshot snap,
    Throwable x);

  void downloadCompletedTaken(
    DownloadSnapshot snap);

  void downloadFailed(
    DownloadSnapshot snap,
    Throwable e);

  void downloadPaused(
    DownloadSnapshot snap);

  void downloadReceivedData(
    DownloadSnapshot snap);

  void downloadResumed(
    DownloadSnapshot snap);

  void downloadStarted(
    DownloadSnapshot snap);

  void downloadStartedReceivingData(
    DownloadSnapshot snap);
}
