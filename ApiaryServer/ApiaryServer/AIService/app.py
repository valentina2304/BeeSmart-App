"""DeepBee comb-cell analysis microservice.

The segmentation, Hough-circle detection and classification pipeline in this
module is adapted from the DeepBee project:

    Alves, T. S., Pinto, M. A., Ventura, P., Neves, C. J., Biron, D. G.,
    Cândido Júnior, A. C., De Paula Filho, P. L., Rodrigues, P. J. (2020).
    "Automatic detection and classification of honey bee comb cells using
    deep learning." Computers and Electronics in Agriculture, 170, 105244.
    https://doi.org/10.1016/j.compag.2020.105244

    Source: https://github.com/AvsThiago/DeepBee-source
    Copyright (c) 2019 the DeepBee authors. Licensed under the MIT License.

See THIRD_PARTY_NOTICES.md at the repository root for the full license text.
"""

import base64
import math
import os
import threading
import time
import traceback
from collections import Counter

import cv2
import keras
import numpy as np
import tensorflow as tf
from flask import Flask, jsonify, request
from keras import backend as K
from keras.applications.imagenet_utils import preprocess_input

app = Flask(__name__)

# DeepBee model files. The class order must match the training/export order
# used by the original project, otherwise the numeric predictions are decoded
# into the wrong biological categories.
CLASSIFICATION_MODEL_PATH = "classification.h5"
SEGMENTATION_MODEL_PATH = "segmentation.h5"
CLASSES = ["Capped", "Eggs", "Honey", "Larves", "Nectar", "Other", "Pollen"]

# Inference constants from the DeepBee source code.
IMG_SIZE = 224
CLASSIFICATION_BATCH_SIZE = int(os.environ.get("DEEPBEE_CLASSIFICATION_BATCH_SIZE", "64"))
MEAN_RADIUS_DEFAULT = 32
MAX_IMAGE_BYTES = int(os.environ.get("DEEPBEE_MAX_IMAGE_BYTES", str(6 * 1024 * 1024)))

# The public DeepBee code reconstructs a 6000x4000 segmentation mask. That is
# faithful but very expensive on small hosted CPUs, so production inference uses
# the same patch model on the received image, capped to a configurable long edge.
SEGMENTATION_TILE_SIZE = 512
SEGMENTATION_TILE_OUTPUT = 482
SEGMENTATION_TILE_MARGIN = 15
SEGMENTATION_MAX_SIDE = int(os.environ.get("DEEPBEE_SEGMENTATION_MAX_SIDE", "1600"))
SEGMENTATION_BATCH_SIZE = int(os.environ.get("DEEPBEE_SEGMENTATION_BATCH_SIZE", "16"))

# Quality gates. These reject images that are unlikely to produce a useful
# apicultural result before the model is allowed to make a confident-looking
# but misleading prediction.
MIN_IMAGE_SIDE = 480
MIN_BLUR_VARIANCE = 55.0
MIN_CONTRAST_STD = 18.0
MIN_BRIGHTNESS = 35.0
MAX_BRIGHTNESS = 225.0
MIN_DETECTED_CELLS = 20
MIN_RELIABLE_CONFIDENCE = 0.60
MAX_LOW_CONFIDENCE_RATIO = 0.75

# Reuse a single TF1 graph/session. Keras 2.x + TF1 is not naturally
# thread-safe in Flask, so model inference is serialized with MODEL_LOCK.
session = tf.Session()
K.set_session(session)
classification_model = keras.models.load_model(CLASSIFICATION_MODEL_PATH, compile=False)
segmentation_model = (
    keras.models.load_model(SEGMENTATION_MODEL_PATH, compile=False)
    if os.path.exists(SEGMENTATION_MODEL_PATH)
    else None
)
graph = tf.get_default_graph()
MODEL_LOCK = threading.Lock()


def empty_results():
    return {cls: 0 for cls in CLASSES}


def empty_classification_metrics():
    """Confidence metrics returned when no cell could be classified."""
    return {
        "classifiedCells": 0,
        "lowConfidenceCells": 0,
        "lowConfidenceRatio": 1.0,
        "meanConfidence": 0.0,
        "cellDetectionsReturned": 0,
    }


def decode_image_payload(data):
    if not isinstance(data, str) or not data.strip():
        raise ValueError("image_base64 must be a non-empty string")

    payload = data.strip()
    if "," in payload:
        header, payload = payload.split(",", 1)
        header = header.lower()
        if not header.startswith("data:image/") or "base64" not in header:
            raise ValueError("image_base64 must be a Base64 image data URI")

    payload = "".join(payload.split())
    try:
        img_bytes = base64.b64decode(payload, validate=True)
    except Exception:
        raise ValueError("image_base64 must be valid Base64")

    if not img_bytes:
        raise ValueError("image_base64 decoded to an empty payload")
    if len(img_bytes) > MAX_IMAGE_BYTES:
        raise OverflowError(
            "Image payload is too large. Compress or resize the frame before analysis."
        )

    return img_bytes


def quality_error(status, message, metrics):
    return {
        "status": status,
        "message": message,
        "results": empty_results(),
        "cellDetections": [],
        "quality": metrics,
    }


def image_quality_metrics(img_bgr):
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    height, width = gray.shape[:2]
    blur_variance = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    brightness = float(np.mean(gray))
    contrast = float(np.std(gray))

    return {
        "width": int(width),
        "height": int(height),
        "blurVariance": round(blur_variance, 2),
        "brightness": round(brightness, 2),
        "contrast": round(contrast, 2),
        "segmentationEnabled": segmentation_model is not None,
    }


def validate_basic_image_quality(img_bgr):
    metrics = image_quality_metrics(img_bgr)

    if metrics["width"] < MIN_IMAGE_SIDE or metrics["height"] < MIN_IMAGE_SIDE:
        return quality_error(
            "low_quality",
            "Fotografia este prea mica pentru analiza. Incearca o poza mai aproape de rama, la rezolutie mai buna.",
            metrics,
        )

    if metrics["blurVariance"] < MIN_BLUR_VARIANCE:
        return quality_error(
            "low_quality",
            "Fotografia pare neclara. Refotografiaza rama tinand telefonul stabil si focalizat pe fagure.",
            metrics,
        )

    if metrics["brightness"] < MIN_BRIGHTNESS:
        return quality_error(
            "low_quality",
            "Fotografia este prea intunecata pentru analiza. Incearca mai multa lumina, fara umbre puternice.",
            metrics,
        )

    if metrics["brightness"] > MAX_BRIGHTNESS:
        return quality_error(
            "low_quality",
            "Fotografia este supraexpusa. Incearca sa eviti lumina directa sau reflexiile puternice.",
            metrics,
        )

    if metrics["contrast"] < MIN_CONTRAST_STD:
        return quality_error(
            "low_quality",
            "Fotografia are contrast prea mic. Incearca o poza mai clara, cu fagurele bine luminat.",
            metrics,
        )

    return None


def contours_from_mask(mask):
    found = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    return found[-2]


def full_frame_mask(img_bgr):
    height, width = img_bgr.shape[:2]
    return np.full((height, width), 255, dtype=np.uint8), (0, 0, width, height)


def resize_for_segmentation(img_bgr):
    if SEGMENTATION_MAX_SIDE <= 0:
        return img_bgr, 1.0

    height, width = img_bgr.shape[:2]
    long_edge = max(height, width)
    if long_edge <= SEGMENTATION_MAX_SIDE:
        return img_bgr, 1.0

    scale = SEGMENTATION_MAX_SIDE / float(long_edge)
    resized = cv2.resize(
        img_bgr,
        (max(1, int(round(width * scale))), max(1, int(round(height * scale)))),
        interpolation=cv2.INTER_AREA,
    )
    return resized, scale


def segment_comb_region(img_bgr):
    """Locate the comb area with the DeepBee patch segmentation model.

    Adapted from DeepBee: the image is tiled into overlapping patches, each
    patch is segmented and the predictions are stitched back into a full mask.
    Returns ``(comb_mask, bounding_rect, debug)``, or ``(None, None, debug)``
    when no comb contour is found. Falls back to a full-frame mask when the
    segmentation model is unavailable.
    """
    if segmentation_model is None:
        mask, rect = full_frame_mask(img_bgr)
        return mask, rect, {"segmentationTiles": 0, "segmentationScale": 1.0}

    original_height, original_width = img_bgr.shape[:2]
    img, scale = resize_for_segmentation(img_bgr)
    height, width = img.shape[:2]

    positions = [
        (x, y, min(SEGMENTATION_TILE_OUTPUT, width - x), min(SEGMENTATION_TILE_OUTPUT, height - y))
        for y in range(0, height, SEGMENTATION_TILE_OUTPUT)
        for x in range(0, width, SEGMENTATION_TILE_OUTPUT)
    ]

    border_type = (
        cv2.BORDER_REFLECT
        if height > SEGMENTATION_TILE_SIZE and width > SEGMENTATION_TILE_SIZE
        else cv2.BORDER_REPLICATE
    )
    padded = cv2.copyMakeBorder(
        img,
        SEGMENTATION_TILE_MARGIN,
        SEGMENTATION_TILE_SIZE,
        SEGMENTATION_TILE_MARGIN,
        SEGMENTATION_TILE_SIZE,
        border_type,
    )

    x_batch = np.zeros((len(positions), 128, 128, 3), dtype=np.uint8)
    for index, (x, y, _, _) in enumerate(positions):
        window = padded[y : y + SEGMENTATION_TILE_SIZE, x : x + SEGMENTATION_TILE_SIZE]
        x_batch[index] = cv2.resize(window, (128, 128), interpolation=cv2.INTER_AREA)

    preds = segmentation_model.predict(
        x_batch,
        batch_size=SEGMENTATION_BATCH_SIZE,
        verbose=0,
    )
    preds = (preds > 0.5).astype(np.uint8)

    reconstructed_mask = np.zeros((height, width), dtype=np.uint8)
    for pred, (x, y, tile_width, tile_height) in zip(preds, positions):
        tile = cv2.resize(
            np.squeeze(pred),
            (SEGMENTATION_TILE_SIZE, SEGMENTATION_TILE_SIZE),
            interpolation=cv2.INTER_LINEAR,
        )
        crop = tile[
            SEGMENTATION_TILE_MARGIN : SEGMENTATION_TILE_MARGIN + tile_height,
            SEGMENTATION_TILE_MARGIN : SEGMENTATION_TILE_MARGIN + tile_width,
        ]
        reconstructed_mask[y : y + tile_height, x : x + tile_width] = crop

    reconstructed_mask = (reconstructed_mask * 255).astype(np.uint8)

    if scale != 1.0:
        reconstructed_mask = cv2.resize(
            reconstructed_mask,
            (original_width, original_height),
            interpolation=cv2.INTER_NEAREST,
        )

    contours = contours_from_mask(reconstructed_mask)
    debug = {
        "segmentationTiles": int(len(positions)),
        "segmentationScale": round(float(scale), 4),
        "segmentationWidth": int(width),
        "segmentationHeight": int(height),
    }
    if not contours:
        return None, None, debug

    max_contour = max(contours, key=cv2.contourArea)
    if cv2.contourArea(max_contour) <= 0:
        return None, None, debug

    comb_mask = np.zeros_like(reconstructed_mask)
    cv2.drawContours(comb_mask, [max_contour], 0, 255, -1)
    bounding_rect = cv2.boundingRect(max_contour)
    return comb_mask, bounding_rect, debug


def find_circles_deepbee(img_bgr, mask, bounding_rect):
    """Detect comb cells as circles using the DeepBee Hough strategy.

    Adapted from DeepBee: a coarse radius sweep estimates the dominant cell
    radius, then a refined HoughCircles pass detects cells around that radius.
    Only circle centers that fall inside the segmentation ``mask`` are kept.
    Returns an ``Nx3`` array of ``(x, y, radius)`` in full-image coordinates.
    """
    x, y, width, height = bounding_rect
    if width <= 0 or height <= 0:
        return np.array([])

    roi = np.copy(img_bgr[y : y + height, x : x + width])
    if roi.size == 0:
        return np.array([])

    # DeepBee detects circles on the red channel after local contrast
    # equalization and edge-preserving denoising.
    red_channel = cv2.split(roi)[2]
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(9, 9))
    filtered = clahe.apply(red_channel)
    filtered = cv2.bilateralFilter(filtered, 5, 50, 50)

    all_points = np.array([])
    for radius in range(5, 50, 5):
        points = cv2.HoughCircles(
            filtered,
            cv2.HOUGH_GRADIENT,
            dp=2,
            minDist=12,
            param1=145,
            param2=55,
            minRadius=radius + 1,
            maxRadius=radius + 5,
        )
        if points is not None:
            points = points[0][:, :3].astype(np.int32)
            all_points = np.vstack((all_points, points)) if all_points.size else points

    if all_points.size == 0:
        best_radius = 33
    else:
        best_radius = int(np.bincount(all_points[:, -1].astype(int)).argmax())

    min_dist = best_radius * 2 - ((best_radius * 9 / 26) + 75 / 26)
    radius_margin = max(2, math.floor(best_radius * 0.1))
    min_radius = max(3, best_radius - radius_margin)
    max_radius = max(min_radius + 1, best_radius + radius_margin)

    points = cv2.HoughCircles(
        filtered,
        cv2.HOUGH_GRADIENT,
        dp=3,
        minDist=max(1, min_dist),
        param1=100,
        param2=25,
        minRadius=int(min_radius),
        maxRadius=int(max_radius),
    )

    if points is None:
        return np.array([])

    points = points[0][:, :3].astype(np.int32)
    points = points[(points[:, 0] >= 0) & (points[:, 0] < width)]
    points = points[(points[:, 1] >= 0) & (points[:, 1] < height)]
    if len(points) == 0:
        return np.array([])

    points[:, 0] += x
    points[:, 1] += y

    image_height, image_width = mask.shape[:2]
    points = points[
        (points[:, 0] >= 0)
        & (points[:, 0] < image_width)
        & (points[:, 1] >= 0)
        & (points[:, 1] < image_height)
    ]
    if len(points) == 0:
        return np.array([])

    return points[mask[points[:, 1], points[:, 0]] > 0]


def extract_circles_with_points(image_rgb, points, output_size=IMG_SIZE, mean_radius_default=MEAN_RADIUS_DEFAULT):
    """Crop and resize each detected cell to the classifier input size.

    Adapted from DeepBee: radii are rescaled relative to a reference mean
    radius and crops are taken from a reflect-padded image so cells near the
    border are not lost. Returns the resized crops alongside the points that
    were actually kept (a crop falling fully outside the image is dropped).
    """
    if len(points) == 0:
        return np.array([]), np.empty((0, 3), dtype=np.int32)

    original_points = np.copy(points).astype(np.int32)
    pts = np.copy(points).astype(np.float32)
    pts[:, 2] = np.maximum(1.0, np.floor(pts[:, 2] / 2.0))
    pts[:, 2] = output_size / mean_radius_default * pts[:, 2]

    border_size = int(math.ceil(float(pts[:, 2].max()))) + 1
    padded = cv2.copyMakeBorder(
        image_rgb,
        border_size,
        border_size,
        border_size,
        border_size,
        cv2.BORDER_REFLECT,
    )
    pts[:, [0, 1]] += border_size

    rois = []
    kept_points = []
    for original_point, (center_x, center_y, radius) in zip(original_points, pts):
        radius = max(2, int(round(float(radius))))
        center_x = int(round(float(center_x)))
        center_y = int(round(float(center_y)))
        crop = padded[center_y - radius : center_y + radius, center_x - radius : center_x + radius]
        if crop.size == 0:
            continue
        rois.append(cv2.resize(crop, (output_size, output_size), interpolation=cv2.INTER_AREA))
        kept_points.append(original_point)

    return np.asarray(rois), np.asarray(kept_points, dtype=np.int32)


def extract_circles(image_rgb, points, output_size=IMG_SIZE, mean_radius_default=MEAN_RADIUS_DEFAULT):
    rois, _ = extract_circles_with_points(image_rgb, points, output_size, mean_radius_default)
    return rois


def ratio(value, denominator):
    if denominator <= 0:
        return 0.0
    return round(float(value) / float(denominator), 6)


def cell_detection_from_prediction(point, class_index, confidence, image_width, image_height):
    x = int(point[0])
    y = int(point[1])
    radius = int(point[2])
    return {
        "x": x,
        "y": y,
        "radius": radius,
        "normalizedX": ratio(x, image_width),
        "normalizedY": ratio(y, image_height),
        "normalizedRadius": ratio(radius, max(image_width, image_height)),
        "className": CLASSES[int(class_index)],
        "confidence": round(float(confidence), 4),
    }


def classify_cells(img_bgr, points):
    if len(points) == 0:
        return empty_results(), empty_classification_metrics(), []

    image_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
    image_height, image_width = img_bgr.shape[:2]
    counts = Counter()
    low_confidence = 0
    confidence_sum = 0.0
    classified = 0
    cell_detections = []

    # Keep memory bounded on small Azure Container Apps instances. Building all
    # 224x224 crops as one float32 array can exceed 2 GiB for dense frames.
    for start in range(0, len(points), CLASSIFICATION_BATCH_SIZE):
        point_batch = points[start : start + CLASSIFICATION_BATCH_SIZE]
        blob_imgs, kept_points = extract_circles_with_points(image_rgb, point_batch)
        if len(blob_imgs) == 0:
            continue

        blob_imgs = preprocess_input(blob_imgs.astype(np.float32))
        scores = classification_model.predict(
            blob_imgs,
            batch_size=CLASSIFICATION_BATCH_SIZE,
            verbose=0,
        )
        predictions = np.argmax(scores, axis=1)
        confidences = np.amax(scores, axis=1)

        counts.update(predictions)
        low_confidence += int(np.sum(confidences < MIN_RELIABLE_CONFIDENCE))
        confidence_sum += float(np.sum(confidences))
        classified += int(len(predictions))
        for point, class_index, confidence in zip(kept_points, predictions, confidences):
            cell_detections.append(
                cell_detection_from_prediction(point, class_index, confidence, image_width, image_height)
            )

    if classified == 0:
        return empty_results(), empty_classification_metrics(), []

    results = {label: int(counts[index]) for index, label in enumerate(CLASSES)}
    metrics = {
        "classifiedCells": classified,
        "lowConfidenceCells": low_confidence,
        "lowConfidenceRatio": round(float(low_confidence / classified), 3) if classified else 1.0,
        "meanConfidence": round(float(confidence_sum / classified), 4) if classified else 0.0,
        "cellDetectionsReturned": int(len(cell_detections)),
        "classificationBatchSize": CLASSIFICATION_BATCH_SIZE,
    }
    return results, metrics, cell_detections


def process_frame(img_bgr):
    analysis_started = time.perf_counter()
    basic_quality_error = validate_basic_image_quality(img_bgr)
    if basic_quality_error is not None:
        return basic_quality_error

    metrics = image_quality_metrics(img_bgr)
    timings = {}

    with MODEL_LOCK:
        with graph.as_default():
            K.set_session(session)
            started = time.perf_counter()
            mask, bounding_rect, segmentation_metrics = segment_comb_region(img_bgr)
            timings["segmentationMs"] = int((time.perf_counter() - started) * 1000)
            metrics.update(segmentation_metrics)
            if mask is None or bounding_rect is None:
                metrics["detectedCells"] = 0
                metrics["timingsMs"] = timings
                metrics["totalMs"] = int((time.perf_counter() - analysis_started) * 1000)
                return quality_error(
                    "not_comb_image",
                    "Nu am detectat rama in fotografie. Incarca o poza cu fagurele ocupand cea mai mare parte a imaginii.",
                    metrics,
                )

            x, y, width, height = bounding_rect
            metrics["combBoundingBox"] = {
                "x": int(x),
                "y": int(y),
                "width": int(width),
                "height": int(height),
            }
            metrics["combMaskCoverage"] = round(float(np.count_nonzero(mask) / mask.size), 4)

            started = time.perf_counter()
            cells = find_circles_deepbee(img_bgr, mask, bounding_rect)
            timings["circleDetectionMs"] = int((time.perf_counter() - started) * 1000)
            detected_count = int(len(cells))
            metrics["detectedCells"] = detected_count

            if detected_count < MIN_DETECTED_CELLS:
                metrics["timingsMs"] = timings
                metrics["totalMs"] = int((time.perf_counter() - analysis_started) * 1000)
                return quality_error(
                    "not_comb_image",
                    "Am detectat prea putine celule pentru o analiza sigura. Apropie camera de fagure si incadreaza rama frontal.",
                    metrics,
                )

            started = time.perf_counter()
            results, confidence_metrics, cell_detections = classify_cells(img_bgr, cells)
            timings["classificationMs"] = int((time.perf_counter() - started) * 1000)
            metrics.update(confidence_metrics)

    metrics["timingsMs"] = timings
    metrics["totalMs"] = int((time.perf_counter() - analysis_started) * 1000)

    if metrics["classifiedCells"] == 0 or metrics["lowConfidenceRatio"] > MAX_LOW_CONFIDENCE_RATIO:
        return {
            "status": "uncertain_analysis",
            "message": "Imaginea nu seamana suficient cu o rama analizabila sau modelul are incredere scazuta. Refotografiaza rama mai clar si mai aproape.",
            "results": results,
            "cellDetections": cell_detections,
            "quality": metrics,
        }

    return {
        "status": "success",
        "results": results,
        "cellDetections": cell_detections,
        "quality": metrics,
    }


@app.route("/health", methods=["GET"])
def health():
    return jsonify(
        {
            "status": "running",
            "classificationModel": os.path.exists(CLASSIFICATION_MODEL_PATH),
            "segmentationModel": segmentation_model is not None,
            "classes": CLASSES,
            "segmentationMaxSide": SEGMENTATION_MAX_SIDE,
            "segmentationBatchSize": SEGMENTATION_BATCH_SIZE,
            "classificationBatchSize": CLASSIFICATION_BATCH_SIZE,
            "maxImageBytes": MAX_IMAGE_BYTES,
        }
    )


@app.route("/analyze", methods=["POST"])
def analyze_frame():
    started = time.perf_counter()
    try:
        data = request.get_json(silent=True)
        if not data or "image_base64" not in data:
            return jsonify({"status": "error", "message": "Missing image_base64"}), 400

        try:
            img_bytes = decode_image_payload(data["image_base64"])
        except OverflowError as exc:
            return jsonify({"status": "error", "message": str(exc)}), 413
        except ValueError as exc:
            return jsonify({"status": "error", "message": str(exc)}), 400

        nparr = np.frombuffer(img_bytes, np.uint8)
        img_bgr = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img_bgr is None:
            return jsonify({"status": "error", "message": "Invalid image payload"}), 400

        print(
            "DeepBee analyze start "
            f"payloadBytes={len(img_bytes)} width={img_bgr.shape[1]} height={img_bgr.shape[0]}",
            flush=True,
        )
        result = process_frame(img_bgr)
        quality = result.get("quality")
        if isinstance(quality, dict):
            quality["payloadBytes"] = int(len(img_bytes))
        print(
            "DeepBee analyze end "
            f"status={result.get('status')} totalMs={quality.get('totalMs') if isinstance(quality, dict) else None} "
            f"detected={quality.get('detectedCells') if isinstance(quality, dict) else None} "
            f"classified={quality.get('classifiedCells') if isinstance(quality, dict) else None} "
            f"elapsedMs={int((time.perf_counter() - started) * 1000)}",
            flush=True,
        )
        return jsonify(result)

    except Exception as exc:
        print("DeepBee analyze error", str(exc), flush=True)
        print(traceback.format_exc(), flush=True)
        return jsonify({"status": "error", "message": str(exc)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
