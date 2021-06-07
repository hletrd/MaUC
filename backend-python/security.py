from user import User

users = [
    User(1, 'usman', 'baan'),
    User(2, 'jiyong', 'youn'),
]

username_table = {u.username: u for u in users}
userid_table = {u.id: u for u in users}
#we are doing above mappping so that we dont have to iterate over our list many times
def authenticate(username,password):
    user = username_table.get(username, None)
    if user and user.password==password:
        return user

def identity(payload):
    user_id = payload['identity']
    return userid_table.get(user_id, None)

