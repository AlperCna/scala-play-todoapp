package dtos

case class AdminDashboardResponse(
                                   totalUsers: Int,
                                   activeUsers: Int,
                                   passiveUsers: Int,
                                   totalTodos: Int
                                 )