# LAPD: Hidden Spy Camera Detection using Smartphone Time-of-Flight Sensors

<a href="https://dl.acm.org/doi/abs/10.1145/3485730.3485941" target="_blank"><img src="https://img.shields.io/badge/doi-10.1145%2F3485730.3485941-brightgreen"></img></a> <a href="https://github.com/frizensami/lapd/blob/main/LICENSE.md" target="_blank"><img src="https://img.shields.io/badge/license-MIT-blue"></img></a>

Authors: [Sriram Sami](https://github.com/frizensami/), [Sean Rui Xiang Tan](https://github.com/trufflepirate/), [Bangjie Sun](https://github.com/SunBangjie), [Jun Han](https://www.junhan.org/).

## Summary

This repository includes code for **LAPD** ("Laser Assisted Photography Detection"), a novel hidden camera detection and localization system that leverages the time-of-flight (ToF) sensor on commodity smartphones. We implement LAPD as a smartphone app on Android that emits laser signals from the ToF sensor, and uses computer vision and machine learning techniques to locate the unique reflections from hidden cameras.

LAPD was presented at [ACM SenSys 2021](https://sensys.acm.org/2021/) in Coimbra, Portugal [(presentation video)](https://www.youtube.com/watch?v=t4Txdhlji4k).


### [Project Website](https://www.cyphy-lab.org/research/lapd) | [Demo Video](https://www.youtube.com/watch?v=AFjGQNaqmXAf) | [Research Paper (Open Access)](https://dl.acm.org/doi/abs/10.1145/3485730.3485941)

<br/>

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Summary](#summary)
- [Important Disclaimers](#important-disclaimers)
- [Limitations](#limitations)
    - [Basic UI/UX](#basic-uiux)
    - [RGB-ToF Manual Alignment](#rgb-tof-manual-alignment)
    - [Assumed Hidden Camera Viewing Direction](#assumed-hidden-camera-viewing-direction)
    - [Single ARCore Anchor + RelativeAnchor Stability](#single-arcore-anchor--relativeanchor-stability)
    - [Fixed portrait mode](#fixed-portrait-mode)
- [Requirements, Building, and Installation](#requirements-building-and-installation)
- [Usage](#usage)
    - [Example RGB / ToF  images from LAPD](#example-rgb--tof--images-from-lapd)
- [Technical Information](#technical-information)
    - [Settings](#settings)
    - [Machine Learning Model](#machine-learning-model)
- [License and Citation](#license-and-citation)

<!-- markdown-toc end -->


## Important Disclaimers

- This Android application code is a **research prototype** that can be built on and **not a consumer-facing mobile application**. Please do not use this in a mission-critical setting as-is.
- Please make sure to read the [limitations](#limitations) section to understand why this is the case, and what to expect.


## Limitations

Due to current ARCore limitations and for testing simplicity, LAPD includes numerous workarounds and has a few built-in limitations. This section describes the most significant of these.

### Basic UI/UX

As a research prototype, most user interface elements are very basic, and are not likely to be intuitive. There is no tutorial mode, and many changes must be done directly in code.

### RGB-ToF Manual Alignment

The RGB (color, ARCore) images and ToF images from the camera are not aligned by default. This misalignment (if not corrected) causes suspected hidden camera locations to be displayed at incorrect positions on the color image. There is also no existing API to align these images automatically.

Our current workaround is to apply a simple transformation between color and ToF images: multiply/divide coordinates by a constant scale factor and add/subtract an offset.

**These scale and offset values (one for each axis) must be set within `PhoneLidarConfig.java` for each smartphone. They are not constant across phones of the same make and model.**

### Assumed Hidden Camera Viewing Direction

For simplicity during testing, we assume all hidden cameras to be directly facing the smartphone. Specifically, we take the orientation of the smartphone when the LAPD app initializes, and assume all cameras are pointing directly at it.

**For now, you should start the LAPD app while directly facing the suspected location of hidden cameras.**

### Single ARCore Anchor + RelativeAnchor Stability

ARCore anchors (virtual objects tracked in 3D) cannot usually be placed at arbitrary locations. They will snap to a point that ARCore can track with high confidence over time. As hidden cameras are small, this places suspected hidden camera markers at misleading locations.

Solutions using Instant Placement also are not particularly reliable as there is no guarantee of the persistence of such anchors.

We work around this by creating one _reference anchor_ that is tracked with high confidence, and creating all other anchors _relative_ to that reference. See `createReferenceAnchors` and `RelativeAnchor` for this behavior.

**The scene must have sufficient detail for ARCore to have a stable tracking solution (e.g., scanning a blank white wall is not likely to work well)**

### Fixed portrait mode

To simplify transforms and enable fast prototyping, this app is locked to a portrait orientation for now (though UI elements have been rotated to make it seem landscape). This is mostly a legacy decision. Note that enabling landscape orientations without changing the transformation functions or anything else is likely to cause bugs. 

## Requirements, Building, and Installation

- You must use a **Samsung S20+, S20+ Ultra 5G, or Note 10+** smartphone (as they have ToF cameras and we have tested on them)
  - Primarily tested on an S20+ running Android 11, One UI 3.1, kernel 4.19.87-22520512
  - Other Android smartphones with a time-of-flight sensor _may_ work, but LAPD has only been tested on the phones listed above.
- You should ideally have some experience with Android programming and Java.
- Download Android Studio if you do not have it already.
- Clone this codebase, build it with Android Studio, and install the app into your smartphone. 
- We will now need to do a few rounds of re-building and re-installing the app to configure LAPD properly.


## Usage

**Please see the video and research paper above** to understand the process of scanning a region for hidden cameras with LAPD. Furthermore, please read the [limitations](#limitations) section thoroughly before using LAPD. The high-level steps of using LAPD are:

1. (Before compiling the code / uploading the APK) Set the correct ToF camera resolution for your specific phone model in `PhoneLidarConfig.java` under `cameraDimensionMap`.
  - The setting may already be correct (e.g., many S20+ phones do have model number `samsung SM-G985F`), but please verify this as other users have encountered S20+ phones with different model numbers.
  - If you don't know the resolution, change the values around until the ToF images shown by LAPD (click the center button while the app is running to see this view) are not tearing/duplicated  
2. (Through multiple cycles of recompile -> run LAPD -> recompile...) Align the RGB and ToF images by changing the scale and offset factors in `PhoneLidarConfig.java` in `defaultManualTransform`
  - The RGB and ToF images will not be fully aligned since they come from different cameras. 
  - You should open LAPD, point it at a reflective object (or hidden camera), and then keep switching between the ToF and RGB camera mode with the center button.
  - In ToF mode: Find an object or surface that causes the ToF camera image to display green squares on it (possible camera detected)
    - If no green squares appear (perhaps because you don't have a hidden camera on hand), you can increase the sensitivity (increasing the false positive rate) of LAPD by moving the sensitivity slider from 0.5 to the left. This should cause more green squares to appear.
  - In RGB mode: Note the position of the green circles in the RGB image. 
  - The goal is to make the green squares in the ToF image align with the green circles in the RGB image. That is: if a green square appears on an object in the ToF images, it should have a corresponding green circle in the RGB image.
  - You should modify the scale and offset factors of `defaultManualTransform` until you achieve this.
3. Now you can start using LAPD properly. Close the app fully and ensure using your phone task manager that LAPD is no longer running. 
4. [Setting the image plane] Open the LAPD app while facing towards a potential hidden camera location.
5. [ARCore Initialization] Move the phone around to gather data for ARCore to find a stable tracking solution. A blue rectangle will appear to show the location of the reference anchor point and its axes.
6. [Bounding Box Selection] To scan an object, draw a bounding box around it by placing your finger on the screen at the top-left of the object and dragging it to the bottom right.
  - A bounding box should appear around the object. If the bounding box is not correctly drawn, press the "play" button to clear it.
7. [Ideal Distance Scan] A bar should appear on the left side, and as you move closer and further from the object, it should eventually turn entirely green.
  - Once this process is completed, LAPD has calculated the "ideal distance" to stand from the object. LAPD should guide you to stand at the right distance from the object.
8. [Object Scan] At the same time, an unfilled rectangle should appear in the top left of the screen. This is the "scan grid" which you should fill to finish scanning the object for hidden cameras. Move the smartphone around to fill the entire scan grid.
9. When the grid is completely filled (green), any green circles within the object's bounding box indicate suspected hidden camera locations.

Other research groups have successfully used LAPD for their own research, so please reach out to us if you would like some help. We will try to do so if available.

### Example RGB / ToF  images from LAPD

Notice the four UI elements at the bottom, from left to right:
1. Play icon (Reset LAPD): Short press to reset LAPD to Step 6 in Usage section (wait for user to draw bounding box), Long press to make ARCore find a new central anchor
2. Camera swap icon (Change cameras): Swap the main view between the RGB and ToF camera for debugging purposes 
3. Settings icon (Change settings): Access the settings menu to change a variety of options (filters, UI elements, etc)
4. Slider (ML Confidence): Change confidence threshold (between 0 and 1) for the machine learning filter to classify a point as a camera. Set to 0.5 by default. Setting it closer to 0 makes LAPD more trigger-happy (more false positives / fewer false negatives), and vice-versa when setting it closer to 1. We recommend leaving it at 0.

**RGB**
![RGB Image](/assets/rgb.jpg)

**ToF**
![ToF Image](/assets/tof.jpg)

## Technical Information
### Settings

Most relevant settings for the application are centralized in `PhoneLidarConfig.java`. You may want to adjust these values to improve performance for your specific use case. For example, these are some settings for filtering reflections on a per-frame basis:

```java
//////////////////////////
// SINGLE FRAME FILTERS //
//////////////////////////

// Maximum number of saturated pixels per camera blob
// REASON: Empirical:, we don't get more than 3x3 sized blobs at our expected range
public static int MAX_SIZE_CONNECTED_COMPONENT = 9;

// Minimum ratio of area of blob to the area of its bounding box.
// REASON: Calculating the mathematical ratio of perfect circle area to bbox area gives ~0.78.
public static double MIN_COMPACTNESS = 0.75;

// Maximum difference between bounding box width and height in pixels ("squareness" of box)
// REASON: Empirical: this allows for a circular blob with a little slop due to low spatial resolution
public static int MAX_BBOX_WIDTH_HEIGHT_DIFFERENCE_PX = 1;

// Minimum and maximum allowable detection range
// REASON: Empirical: sensor min and max range (20 - 100 cm) to see a camera (test rig), with some slop
private static int KNOWN_RANGE_MIN_CM = 10;
private static int KNOWN_RANGE_MAX_CM = 150;
// Don't change the two below, calculated through setters
public static int KNOWN_RANGE_MIN = KNOWN_RANGE_MIN_CM * 10;
public static int KNOWN_RANGE_MAX = KNOWN_RANGE_MAX_CM * 10;
```

### Machine Learning Model
LAPD uses a machine learning model that does a lot of heavy lifting to filter out false positive reflections [`saved_model.tflite`](https://github.com/frizensami/lapd/blob/main/app/src/main/ml/saved_model.tflite?raw=true). While details of this model are available in the paper, you may also find it helpful to explore it using an online tool like [Netron](https://netron.app/) - just upload the model file to it. 

This model was trained using a significant amount of data that we collected during the course of this project. It includes many reflections from both hidden camera objects and non-hidden camera objects. We are working to release this dataset as well. 


## License and Citation

This work is licensed under an [MIT License][cc-by].

[cc-by]: /LICENSE.md

If you use or reference this software, please consider citing our work as follows:

```bibtex
@inproceedings{samiLAPDHiddenSpy2021,
  title = {{{LAPD}}: {{Hidden Spy Camera Detection}} Using {{Smartphone Time-of-Flight Sensors}}},
  shorttitle = {{{LAPD}}},
  booktitle = {Proceedings of the 19th {{ACM Conference}} on {{Embedded Networked Sensor Systems}}},
  author = {Sami, Sriram and Tan, Sean Rui Xiang and Sun, Bangjie and Han, Jun},
  year = {2021},
  month = nov,
  series = {{{SenSys}} '21},
  pages = {288--301},
  publisher = {{Association for Computing Machinery}},
  address = {{New York, NY, USA}},
  doi = {10.1145/3485730.3485941},
  isbn = {978-1-4503-9097-2},
}
```
