module.exports = (sequelize, DataTypes) => {
  const FriendRequest = sequelize.define('FriendRequest', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    senderId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    receiverId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    status: {
      type: DataTypes.ENUM('pending', 'accepted', 'rejected'),
      defaultValue: 'pending'
    },
    message: {
      type: DataTypes.STRING,
      allowNull: true
    }
  }, {
    tableName: 'friend_requests',
    timestamps: true,
    indexes: [
      {
        unique: true,
        fields: ['senderId', 'receiverId'],
        where: {
          status: 'pending'
        }
      }
    ]
  });

  FriendRequest.associate = (models) => {
    // Sender of the friend request
    FriendRequest.belongsTo(models.User, {
      foreignKey: 'senderId',
      as: 'sender'
    });
    
    // Receiver of the friend request
    FriendRequest.belongsTo(models.User, {
      foreignKey: 'receiverId',
      as: 'receiver'
    });
  };

  return FriendRequest;
}; 