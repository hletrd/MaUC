from flask import Flask, request
from ai_part import *
from flask_restful import Resource, Api, reqparse
from flask_jwt import JWT,jwt_required
from security import authenticate,identity
import io
from PIL import Image
from datetime import datetime
import datetime
import time
from datetime import date


import matplotlib.pyplot as plt


app = Flask(__name__)
api = Api(app)
app.config['SECRET_KEY'] = 'somerandomstring'
jwt=JWT(app,authenticate,identity) #jwt creates a new endpoint which is /auth
class AI(Resource):    

    @jwt_required()
    def post(self):
        token = request.form.get('token')
        frm_id = request.form.get("frm_id")
        img = request.files.get('img')
        img = Image.open(img)
        open_cv_image = np.array(img) 
        img = open_cv_image[:, :, ::-1].copy() 
        today = date.today() 
        filename=str(today.year)+str(today.month)+str(today.day)+str(time.time())+'.png'
        cv2.imwrite("/ImageData/"+filename,img)
        try:
            model = get_model("/model.h5")
            ss = get_status(img, model)
            resp = {'frm_id': frm_id, 'state_id': ss[0],'state':ss[1]}
            return resp

        except:
            ss=[2,"Driver driving well"]
            resp = {'frm_id': frm_id, 'state_id': ss[0],'state':ss[1]}
            return resp
            
            
api.add_resource(AI, '/get_state')
if __name__ == '__main__':
    app.run(host='0.0.0.0',port=7296,debug=True) 
