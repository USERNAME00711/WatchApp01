import numpy as np
import tflite_runtime.interpreter as tflite
from os.path import dirname, join

def process_signal(segment):
    # Load the TFLite model
    filename = join(dirname(__file__), "transformer.tflite")
    interpreter = tflite.Interpreter(model_path=filename)
    interpreter.allocate_tensors()

    # Get input and output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # Convert segment to float32

    segment = np.array(segment)
    segment = segment[:500]
    segment = np.float32(segment)
    segment= np.expand_dims(segment, axis=0)  # Insert batch dimension

    # Set the input tensor with the segment to test

    interpreter.set_tensor(input_details[0]['index'], np.expand_dims(segment, axis=0))



    # Run inference
    interpreter.invoke()

    # Get the predicted output
    predicted_output = interpreter.get_tensor(output_details[0]['index'])

    # Format the result as a string
    predicted_output = str(predicted_output)
   

    return predicted_output