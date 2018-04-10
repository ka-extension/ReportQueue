package models

class User(userKaid: String, userName: String) {
  def kaid: String = userKaid
  def name: String = userName
}

object User {
  def apply(kaid: String, name: String): User = new User(kaid, name)
}