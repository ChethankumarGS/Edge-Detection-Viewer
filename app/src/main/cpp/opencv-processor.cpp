#include "opencv-processor.h"
#include <opencv2/imgproc.hpp>

cv::Mat OpenCVProcessor::processEdgeDetection(const cv::Mat& input) {
    cv::Mat edges;
    cv::Canny(input, edges, 50, 150, 3);
    return edges;
}

cv::Mat OpenCVProcessor::convertYUVtoGray(const cv::Mat& yuv) {
    cv::Mat gray;
    cv::cvtColor(yuv, gray, cv::COLOR_YUV2GRAY_NV21);
    return gray;
}

