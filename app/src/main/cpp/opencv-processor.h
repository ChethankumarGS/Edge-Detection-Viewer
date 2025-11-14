#ifndef OPENCV_PROCESSOR_H
#define OPENCV_PROCESSOR_H

#include <opencv2/core.hpp>

class OpenCVProcessor {
public:
    static cv::Mat processEdgeDetection(const cv::Mat& input);
    static cv::Mat convertYUVtoGray(const cv::Mat& yuv);
};

#endif

