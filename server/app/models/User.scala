package models

case class User(kaid: String, name: String)

object User {
  def apply(kaid: String, name: String): User = new User(kaid, name)
}