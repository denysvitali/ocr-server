# ocr-server

## Introduction

This is the source code of an Android app that uses [Google's MLKit](https://developers.google.com/ml-kit)
to process pictures using the following modules:

- [Text Recognition (OCR)](https://developers.google.com/ml-kit/vision/text-recognition/v2)
- [Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning)


## Usage

1. Build and Run the Android app
2. Copy the IP Address and Port from the notification (e.g: `192.168.1.169:8080`)
3. Use the API

### API

#### `/api/v1/ocr`

**Request**

- Specify the `Content-Type` (e.g: `image/jpeg`)
- Binary Image passed in the body (`--data-binary @/your/file/path.jpg`)

**Response**

You can see the full response generated for 
[this reference image](https://docs.juston.com/images/swiss_qr_bill_en_sample.png) in the
[docs/example/output.json file](docs/example/output.json).

```json
{
  "textBlocks": [
    {
      "text": "Receipt",
      "lines": [
        {
          "text": "Receipt",
          "angle": 0.0,
          "confidence": 0.90290177,
          "recognizedLanguage": "en"
        }
      ],
      "boundingBox": {
        "top": 57,
        "bottom": 90,
        "left": 27,
        "right": 178
      },
      "lang": "und"
    }
    // ...
  ],
  "barcodes": [
    {
      "boundingBox": {
        "top": 147,
        "bottom": 458,
        "left": 548,
        "right": 860
      },
      "displayValue": "SPC\n0200\n1\nCH3908704016075473007\nK\nRobert Schneider AG\n...",
      "rawValue": "SPC\n0200\n1\nCH3908704016075473007\nK\nRobert Schneider AG\n..."
    }
  ]
}
```
