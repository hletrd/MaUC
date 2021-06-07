import numpy as np # linear algebra
import pandas as pd # data processing, CSV file I/O (e.g. pd.read_csv)
import cv2 #opencv library
import glob
import matplotlib.pyplot as plt  #plotting library
import matplotlib.image as mpimg
from sklearn.model_selection import train_test_split
import tensorflow
import random
from keras.callbacks import EarlyStopping
from PIL import Image
import h5py
import os
os.environ['CUDA_VISIBLE_DEVICES'] = '2'

from keras import layers
from keras import models
from keras import optimizers
from keras.preprocessing.image import ImageDataGenerator
from keras.preprocessing.image import img_to_array, load_img
from keras.layers import Conv2D, MaxPooling2D, Flatten, Dense, Dropout,BatchNormalization

def get_model(path):
    model = models.Sequential()

    ## CNN 1
    model.add(Conv2D(32,(3,3),activation='relu',input_shape=(240,240,1)))
    model.add(BatchNormalization())
    model.add(Conv2D(32,(3,3),activation='relu',padding='same'))
    model.add(BatchNormalization(axis = 3))
    model.add(MaxPooling2D(pool_size=(2,2),padding='same'))
    model.add(Dropout(0.2))

    ## CNN 2
    model.add(Conv2D(64,(3,3),activation='relu',padding='same'))
    model.add(BatchNormalization())
    model.add(Conv2D(64,(3,3),activation='relu',padding='same'))
    model.add(BatchNormalization(axis = 3))
    model.add(MaxPooling2D(pool_size=(2,2),padding='same'))
    model.add(Dropout(0.3))

    ## CNN 3
    model.add(Conv2D(128,(3,3),activation='relu',padding='same'))
    model.add(BatchNormalization())
    model.add(Conv2D(128,(3,3),activation='relu',padding='same'))
    model.add(BatchNormalization(axis = 3))
    model.add(MaxPooling2D(pool_size=(2,2),padding='same'))
    model.add(Dropout(0.5))

    ## CNN 3
    #model.add(Conv2D(256,(5,5),activation='relu',padding='same'))
    #model.add(BatchNormalization(axis = 3))
    #model.add(MaxPooling2D(pool_size=(2,2),padding='same'))
    #model.add(Dropout(0.5))

    ## Dense & Output
    model.add(Flatten())
    model.add(Dense(units = 512,activation='relu'))
    model.add(BatchNormalization())
    model.add(Dropout(0.5))
    model.add(Dense(units = 128,activation='relu'))
    model.add(Dropout(0.5))
    model.add(Dense(8,activation='softmax'))
    model.load_weights(path)
    
    return model
    
def get_status(img, model): 
    img = cv2.resize(img,(240,240))
    img = np.array(img).reshape(-1,240,240,1)
    preds = model.predict(img)
    preds = np.argmax(preds[0])
    if preds==9:
        status= 0
        state= 'Not Attentive: Driver is Looking at right'
        return [status, state, preds]
    if preds==8:
        status= 0
        state= 'Not Attentive: Driver is Touching on face'
        return [status, state, preds]
    if preds==7:
        status= 0
        state= 'Not Attentive: Driver is Picking something from back seat'
        return [status, state, preds]
    if preds==6:
        status= 0
        state= 'Not Attentive: Driver is Drinking/Eating'
        return [status, state, preds]
    if preds==5:
        status= 0
        state= 'Not Attentive: Adjusting the Music'
        return [status, state, preds]
    if preds==4:
        status= 0
        state= 'Not Attentive: Driver is talking on mobile phone'
        return [status, state, preds]
    if preds==3:
        status= 0
        state= 'Not Attentive: Driver is Texting on mobile phone'
        return [status, state, preds]
    if preds==2:
        status= 0
        state= 'Not Attentive: Driver is talking on mobile phone'
        return [status, state, preds]
    if preds==1:
        status= 0
        state= 'Not Attentive: Driver is Texting on mobile phone'
        return [status, state, preds]
    if preds==0:
        status= 1
        state= 'Driver is driving well'
        return [status, state, preds]
    
    


