package dtos

case class AdminUserResponse(
                              id: String,
                              username: String,
                              email: String,
                              role: String,
                              isActive: Boolean,
                              createdAt: String
                            )

case class UserPageResponse(
                             users: Seq[AdminUserResponse],
                             currentPage: Int,
                             pageSize: Int,
                             totalItems: Int,
                             totalPages: Int,
                             search: String
                           )